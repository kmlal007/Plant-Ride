# Plant-Ride

A mobility platform for large industrial plants. It covers intra-plant shuttles and commute buses
running on fixed timetables, on-demand rides (exclusive or shared) dispatched to drivers, live GPS
tracking with safety alerts, and charging every ride to a department **cost center** or **project**.
Employees don't pay for rides.

- [`docs/PLAN.md`](docs/PLAN.md): product and architecture plan (problem, competitors, risks, phasing)
- [`docs/DEMO_WALKTHROUGH.md`](docs/DEMO_WALKTHROUGH.md): scripted customer showcase on the pilot plant
- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md): on-premise (default) and cloud deployment, notifications, configuration

## Quick start: pilot showcase

```bash
docker compose --env-file .env.demo --profile demo up -d --build
```

| App | URL | Login (password `password`) |
|---|---|---|
| Admin / control room | http://localhost:8081 | `demo.admin` |
| Employee app (browser build) | http://localhost:8082 | `E301` |
| Driver app (browser build) | http://localhost:8083 | `9000000001` |

This starts **Demo Steel Works (Pilot)**, a fictional plant with 30 days of history. A simulator moves shuttles and
cabs on GPS and runs "bot" drivers, so the plant looks live. Follow the walkthrough for the story.

## Applications

| App | Tech | Who uses it | Path |
|---|---|---|---|
| Backend API | Spring Boot 3 (Java 21), PostgreSQL, Flyway | Everything else | `backend/` |
| Admin web | React + Vite + TypeScript | Admins and the transport control room | `admin-web/` |
| User app | React Native (Expo SDK 57, Expo Router) | Employees and approvers | `user-app/` |
| Driver app | React Native (Expo SDK 57, Expo Router) | Cab drivers | `driver-app/` |

GPS devices aren't an app: they report to a GPS gateway (Traccar), which forwards positions to the backend.

```
 User app ─┐                                   ┌──────────── Spring Boot (modular monolith) ────────────┐
 Driver app├──HTTPS/JSON (JWT)───────────────► │ auth · plant · org · user · fleet · network · tracking │
 Admin web ┘                                   │ ride (booking, dispatch, sweeper) · notification       │
 GPS devices ──► Traccar ──POST /api/tracking─►└────────────────────────┬───────────────────────────────┘
                                                                        ▼
                                                                    PostgreSQL
```

## What works today

**Configuration (admin web)**
- Plants. Multi-plant mode is a system setting: off by default, and an admin can turn it on and switch between plants. Every record belongs to one plant.
- Per-plant rules: speed limit, dispatch radius, driver offer timeout, search timeout, how early scheduled rides are dispatched, whether exclusive rides need approval, and the optional visitor module.
- Departments, cost centers (with monthly budget), projects/WBS, users (employee, driver, dispatcher, admin) with an approving manager, vendors, vehicles (owned by a department, a vendor or the central pool; on-demand or fixed-route; GPS device id), and rate cards.
- Stops, and routes with a metro-style timetable: first and last departure, a departure every N minutes, days of the week, and each stop's minutes after the first stop.

**Shuttles and buses (user app)**
- Nearest stops by estimated walking time.
- Next arrivals at a stop: from the timetable, plus a **live** estimate from GPS when a vehicle is assigned to the route.
- Journey suggestion to a destination stop: direct routes ranked by arrival time.

**On-demand rides**
- Book now or up to 7 days ahead. Choose exclusive or shared, a vehicle type and the number of passengers. Pickup and drop come from the phone's location, a department or a stop. Pick a purpose and the cost center or project to charge.
- Exclusive rides go to the requester's manager for approval (configurable per plant).
- Dispatch:
  - Picks the nearest eligible vehicle: a driver is signed on, the GPS fix is recent, the vehicle is within the radius, has enough seats and matches the type.
  - Department-owned vehicles serve only their own department and are preferred for it.
  - The offer expires if the driver doesn't accept in time, and the ride moves to the next vehicle.
  - A ride still unassigned after the search timeout becomes UNFULFILLED, and the control room can assign it manually.
- Driver flow: accept or decline → arrived → start with the rider's **OTP** → complete. A no-show can be marked after 5 minutes of waiting.
- On completion, distance is measured from the **GPS track**. If the track is missing, the distance is estimated and flagged. Fare = rate card; one cost allocation row is written per ride.
- Visitor/delegate rides (optional module): a host books for a visitor, with an optional gate pass reference. The visitor gets the OTP by SMS through the notification interface.

**Tracking and safety**
- Ingests positions as a generic JSON batch or in Traccar's forward format (speed in knots).
- Positions buffered offline and replayed later go into history without moving the "latest position" backwards.
- Over-speed alerts against the plant limit, at most one per vehicle every 2 minutes.

**Configurable cost policies** (Finance hasn't decided yet, so each is a per-plant setting)
- Department vehicles: own department only, or lent to others when idle (own department is always preferred).
- A department in its own vehicle: charged at the rate card, or recorded at zero.
- A department in a lent vehicle: borrower charged and owner credited, borrower charged only, or free.
- Shuttle and bus monthly running cost: central overhead, one central cost center, or split by headcount. Each month is previewed, then posted once.

**Notifications.** Push uses Firebase (FCM) by default. An on-prem webhook gateway or none can be configured instead. SMS
goes through a webhook. Delivery runs after the database commit, on a background thread. The apps register push tokens and
still poll as a fallback.

**Pilot showcase:** the demo plant, a GPS simulator and bot drivers (`SEED_DEMO=true`).

**Control room and reports (admin web)**
- Dashboard: live map, fleet status, ride counts, safety alerts.
- Ride list with manual assign and cancel.
- Cost center report with budget use and CSV export for Finance.

## Not built yet (and why)

| Gap | Note |
|---|---|
| Push tested against a live Firebase project | The FCM sender is implemented but untested against real Firebase in this environment. The webhook provider and fallback are tested. |
| iOS push | Needs the Firebase iOS SDK set up or an APNs provider. iOS uses polling. |
| Boarding scans for shuttles | Needed before shuttle costs can be allocated by actual use (boardings) instead of headcount. |
| Pooling several shared riders into one vehicle | Phase 2 in the plan. A SHARED ride is priced at the shared rate but dispatched alone. |
| Seat reservation on shuttles and buses | Phase 2. Only worth building where data shows buses running full. |
| Corporate SSO (OIDC), HRMS and SAP integration | Username/password + JWT for now. Cost reports export to CSV. |
| Gate pass / visitor management system | Out of scope by decision. Only a gate pass reference is stored. |
| Routing over the plant road network | Distances are straight line × 1.3. Plan: OSRM or GraphHopper on a self-hosted plant map. |
| Vendor portal, invoice reconciliation, compliance-document expiry | Phase 2. |
| Device testing of the mobile apps | Both apps typecheck and bundle for Android and web. The browser builds were tested end to end. The native builds haven't been run on a phone or emulator yet. |

## Running locally (development)

Prerequisites: Java 21, Maven, Node 22, and Docker or a local PostgreSQL 16.

```bash
# Backend against a local PostgreSQL (db/user/password "plantride"), with the pilot plant:
cd backend && SEED_DEMO=true mvn spring-boot:run          # API on http://localhost:8080

cd admin-web && npm install && npm run dev                # http://localhost:5173 (proxies /api)

# Mobile apps (Expo). Set EXPO_PUBLIC_API_URL to an address the phone or emulator can reach:
cd user-app && cp .env.example .env && npm install && npx expo start      # press w for the browser
cd driver-app && cp .env.example .env && npm install && npx expo start
```

Simulate a GPS device:

```bash
curl -X POST localhost:8080/api/tracking/positions -H 'X-Api-Key: dev-tracking-key' \
  -H 'Content-Type: application/json' \
  -d "[{\"deviceId\":\"SIM-CAR-3\",\"lat\":22.7905,\"lng\":86.1902,\"speedKmh\":0,\"fixTime\":\"$(date -u +%FT%TZ)\"}]"
```

For real devices, point them at a Traccar server and enable its JSON position forwarding to
`POST /api/tracking/traccar`, sending the `X-Api-Key` header. Check the exact forwarding configuration keys for your
Traccar version.

## Tests

```bash
cd backend && mvn test                     # unit + end-to-end API tests (H2 in PostgreSQL mode)
cd admin-web && npm run build              # typecheck + production build
cd user-app && npx tsc --noEmit            # typecheck (same for driver-app)
```

The backend's end-to-end tests drive the HTTP API with a controllable clock. They cover:
- the approval → dispatch → OTP → GPS-measured completion → cost report flow
- offer expiry and re-dispatch, search timeout and manual assignment
- department-only vehicles, scheduled dispatch, cancellation releasing the vehicle
- the visitor module toggle, multi-plant scoping, tracking API key checks and over-speed debouncing
- each cost policy, shuttle allocation preview and posting, and push device registration
- the pilot plant: it seeds, and a simulated bot driver completes a ride on simulated GPS

## Configuration

See [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md#4-configuration-reference-backend-environment). Plant business rules and
cost policies are edited in *Admin → Plants & settings*.

## Known limitations to address before production

- **Scale-out:** the ride sweeper (and the demo simulator) runs on every instance. That's safe (optimistic locking plus conditional vehicle updates), but it repeats work. Add a scheduler lock such as ShedLock before running more than one instance.
- **Position history:** `vehicle_position` grows by about 50 rows/s at 500 vehicles. Add daily partitioning and a retention job, with the retention period agreed with Legal under the DPDP Act.
- **Login hardening:** no rate limiting or lockout on `/api/auth/login` yet.
- **Map tiles:** the admin map uses public OpenStreetMap tiles by default (`VITE_TILE_URL`). Plant layouts may be confidential, so use a self-hosted tile server.
