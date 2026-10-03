# Plant-Ride — Product & Architecture Plan (Draft v0.1)

Status: **Draft for review.** Open questions in §12 must be answered before
Phase 1 build starts. Nothing here is final.

---

## 1. Problem statement

A large integrated plant (Tata Steel–like) moves people every day in four
distinct ways, with no single system managing any of them:

| # | Movement | Who | Vehicle | Pattern |
|---|---|---|---|---|
| M1 | **Commute** – township/city → plant department | Employees | Company / contract buses | Fixed routes, shift-timed, high volume, repeat daily |
| M2 | **Intra-plant shuttle** – dept → dept | Employees | Shuttles / buses | Fixed routes & stops, timetabled loops |
| M3 | **On-demand ride** – dept → dept, gate → dept | Employees, visitors, delegates | Department-owned or contract cabs | Ad-hoc or pre-scheduled, **exclusive** or **shared** |
| M4 | **Visitor / delegate movement** | Visitors, delegates (no app) | Any of the above | Tied to a gate pass raised by a host employee |

Riders do not pay. Every trip cost must be **charged to a department or
project cost center**.

### What "solves the business problem" means (proposed success metrics)

| Metric | Why it matters | Target (to be baselined) |
|---|---|---|
| Avg. wait time for on-demand ride | Employee productivity, meeting punctuality | < 10 min in plant |
| Vehicle utilisation (% of duty hours with a passenger) | Fleet right-sizing → direct cost saving | +20–30% vs baseline |
| % of trips GPS-verified and auto-costed | Ends manual duty slips & billing disputes | > 95% |
| Vendor invoice variance (claimed km vs GPS km) | Leakage in contract vehicle billing | Measured, then reduced |
| Cost visibility per cost center | Accountability, budget control | 100% trips attributed |
| Shuttle ETA accuracy | Trust in the app | ±2 min for 80% of predictions |

> **Honest observation:** the strongest ROI is usually not the booking UX but
> **(a) fleet utilisation/pooling across departments** and **(b) GPS-verified
> billing of contract vehicles.** Departments each owning under-used vehicles is
> the classic waste pattern. The plan is ordered to deliver these early.

---

## 2. Challenges to the brief (read before anything else)

1. **This is 5 products, not 1.** Commute management, shuttle information,
   on-demand dispatch, visitor management, and fleet/billing. Building all at
   once is the #1 delivery risk. → Phased plan in §10.

2. **"Drivers use GPS devices" is not enough for on-demand rides.** A GPS
   tracker reports position; it cannot receive a job, accept/reject, verify
   pickup (OTP), or mark drop. Options:
   - **A. Driver smartphone app** (Android, Hindi/English, large buttons) – required for cabs doing on-demand trips.
   - **B. Dispatcher calls driver** – fallback only; does not scale, no proof of service.
   - **C. In-vehicle MDT/tablet** – robust, but costly per vehicle.
   → Recommend: **GPS device only** for fixed-route buses/shuttles;
     **GPS device + driver app** for cabs doing on-demand. Drivers are mostly
     contractors → app must work on cheap Android, poor network, minimal typing.

3. **Visitor/gate pass is a security system, not a transport feature.** It
   involves ID verification, safety induction, PPE, escort rules, approvals by
   security. If the plant already has a gate pass / VMS, **integrate, don't
   rebuild.** If none exists, build it as a separate module with its own
   owner (Security), not inside the ride app. → Open question Q1.

4. **"Free for employees" drives over-consumption.** Without entitlement rules
   and budget visibility, exclusive cars become personal taxis. Need policy:
   who can book exclusive vs shared, approval thresholds, budget caps per
   cost center.

5. **Departments will resist sharing "their" vehicles.** Pooling is where the
   savings are, but it is a political change, not a technical one. The system
   should support both *dedicated* and *pooled* vehicles and let departments
   *lend* vehicles to the pool — and make utilisation visible to management.

6. **Plant roads are not on Google Maps.** Internal roads, gates, one-ways,
   restricted zones, rail crossings must be digitised. Plant layout may also
   be confidential → prefer self-hosted maps/routing over sending plant
   geometry to third-party APIs.

7. **Shared rides (pooling) are algorithmically hard** (Uber Pool problem).
   At plant scale a simple insertion heuristic is enough; don't buy/build a
   VRP optimiser in v1.

8. **Cost allocation for shuttles/buses is a policy decision, not a tech one.**
   They are fixed monthly contracts, so "cost per ride" is an allocation
   choice (see §7).

---

## 3. Competitive landscape (what to match, what to differ on)

| Player | Segment | Features worth matching |
|---|---|---|
| **MoveInSync** (IN) | Corporate employee transport | Roster-based commute, on-demand cabs, vendor mgmt, **automated billing**, compliance docs, safety (SOS, geofence) |
| **Routematic** (IN) | Corporate commute | Route optimisation, live tracking, seat booking, analytics |
| **Zeelo** (UK) | Shift-worker shuttles for factories/warehouses | Shift-linked booking, ridership analytics for employer |
| **TransLoc / DoubleMap** | Campus shuttles | Live map + ETA as core value |
| **Via / Spare / RideCo** | On-demand microtransit | Dynamic pooling, dispatcher console |
| **SAP Concur / corporate travel tools** | Travel & expense | Cost center charging, approval workflows, policy |
| **Envoy / Veris / HID VMS** | Visitor management | Pre-registration, host approval, badge, notifications |
| **Traccar** (open source) | GPS platform | Ingests 200+ GPS device protocols — reuse, don't rebuild |

**Differentiators for an industrial plant** (gaps in office-commute products):
plant-internal road map & restricted-zone geofences, speed-limit enforcement
for EHS, gate/vehicle-permit compliance for contract vehicles, meeting-linked
bookings between departments, cost center + project (WBS) charging into SAP.

---

## 4. Personas & channels

| Persona | Channel | Key jobs |
|---|---|---|
| Employee (rider) | Mobile app (SSO) | See next shuttle, book ride (now / scheduled), book for visitor, track, rate |
| Host employee | Mobile app / web | Book rides for visitors/delegates tied to gate pass |
| Visitor / delegate | **No app**: SMS/WhatsApp link + OTP | See vehicle, driver, ETA; share OTP at pickup |
| Approver (manager / cost center owner) | App push + web | Approve exclusive / out-of-policy rides |
| Driver (cab) | Android driver app + GPS device | Accept job, navigate, OTP start, end trip, SOS |
| Driver (bus/shuttle) | GPS device only | Nothing — tracked passively |
| Dispatcher / Transport control room | Web console | Live map, manual assign, override, incidents |
| Department transport coordinator | Web | Manage dept vehicles, lend to pool, see dept costs |
| Vendor (contractor) | Web portal | Vehicles, drivers, compliance docs, monthly statement |
| Finance | Web / SAP | Cost center reports, vendor invoice reconciliation, postings |
| Security / EHS | Web | Over-speed, restricted-zone, SOS alerts; vehicle permits |

---

## 5. Functional scope by module

### 5.1 Organisation & Policy
- Employees, departments, cost centers, projects/WBS (sync from HRMS/SAP).
- Entitlement policy table (not a rules engine):
  `(grade band, ride type, purpose, time window) → auto-approve | manager | CC owner | deny`.
- Budget per cost center per month; soft warning at 80%, hard stop optional.

### 5.2 Fleet & Vendor
- Vehicle: type, capacity, owner (department | vendor | central pool),
  operating mode (dedicated | pooled | fixed-route), GPS device ID.
- Vendors, contracts, **rate cards** (per km, per hour, per trip, monthly fixed, minimum guarantee).
- Drivers: licence, badge, training; **compliance documents with expiry**
  (RC, insurance, fitness, PUC, plant vehicle permit) → auto-block on expiry.
- Duty / shift assignment of vehicles and drivers.

### 5.3 Network (fixed routes)
- Stops (geo point + entry geofence), routes, ordered stop patterns,
  timetables (GTFS-like: `stops`, `routes`, `trips`, `stop_times`).
- Plant road graph (OSM format) for walking & driving routing.

### 5.4 Tracking
- GPS ingestion via **Traccar** → normalised positions → Redis (latest) +
  PostgreSQL/PostGIS (history, partitioned by day).
- Map-matching to plant road graph; stop arrival/departure detection by geofence.
- ETA: position on route + historical segment times by time-of-day. No ML in v1.
- Safety events: over-speed (plant limit), restricted-zone entry, long idle,
  device offline, SOS.

### 5.5 Commute & Shuttle (M1, M2)
- Rider: nearest stops by **walking time** (not straight line), live next
  arrivals, journey suggestion (earliest arrival, ≤1 transfer).
- Commute: employee registers a home stop + shift → used for capacity planning
  and (later) route optimisation.
- Seat reservation: **Phase 2, only on trips flagged "booking required"**
  (peak shift changes). Segment-based capacity, not seat numbers.
- Boarding capture: employee QR scan on bus or ID-card tap (needed for
  ridership-based cost allocation).

### 5.6 On-demand rides (M3)
- Ride types: **Exclusive** (whole vehicle), **Shared** (seat in a pooled ride).
- When: **Now** or **Scheduled** (e.g. for a meeting at 15:00).
- Multi-passenger booking (group of 4 for a meeting), multi-stop.
- Purpose + cost center/project selection (default = employee's CC).
- Dispatch: nearest available eligible vehicle by **driving ETA**, respecting
  ownership rules (dept vehicle first → pool → vendor on-call).
- Shared: insert pickup/drop into an existing trip if detour ≤ X min for all
  riders and capacity allows; otherwise new trip.
- OTP at pickup, auto-complete at drop geofence as fallback.
- Cancellation rules, no-show handling (driver waits N min, then no-show; repeated
  no-shows reported to manager).

### 5.7 Visitors & Delegates (M4)
- Host links ride booking to a **Visit** (gate pass reference from VMS, or
  created here if no VMS exists).
- Visitor receives SMS/WhatsApp: vehicle no., driver, live tracking link, OTP.
- Gate entry: vehicle + visitor list shared with security (integration).
- Cost charged to host's CC/project.

### 5.8 Billing & Cost allocation
- Every completed trip → **costing record** with GPS-measured km & duration,
  rate card applied, split across cost centers.
- Monthly vendor statement auto-generated; vendor accepts/disputes lines.
- Finance export / SAP posting (cost center + WBS), idempotent.

### 5.9 Notifications
- Push (FCM), SMS, WhatsApp Business API; email for approvals/statements.

### 5.10 Control room & reporting
- Live map of all vehicles, filter by owner/type/status.
- Manual dispatch & reassignment; incident handling.
- Reports: utilisation by vehicle/department, cost by CC, wait times,
  ETA accuracy, vendor performance, safety events.

---

## 6. Key state machines

### On-demand ride
```
REQUESTED ──policy──► PENDING_APPROVAL ──approve──► CONFIRMED
     │                       │ reject                  │
     │ auto-approve          ▼                         │ (scheduled: wait until T-lead)
     └──────────────────► REJECTED                     ▼
                                               DISPATCHING ──no vehicle (timeout)──► UNFULFILLED → escalate to dispatcher
                                                     │ driver accepts
                                                     ▼
                                               ASSIGNED → DRIVER_EN_ROUTE → ARRIVED ──OTP──► IN_PROGRESS → COMPLETED → COSTED
                                                                              │ wait > N min
                                                                              ▼
                                                                           NO_SHOW (costed per policy)
CANCELLED possible from any state before IN_PROGRESS (late cancel may be costed).
```

### Vehicle availability
`OFF_DUTY → AVAILABLE → ASSIGNED → ON_TRIP → AVAILABLE`, plus
`BLOCKED` (compliance expired / device offline / breakdown).

---

## 7. Cost allocation model

| Movement | Recommended allocation | Alternative |
|---|---|---|
| Exclusive ride (vendor) | Rate card × GPS km/time → booking CC | Fixed per-trip slab |
| Exclusive ride (dept-owned vehicle used by other dept) | Internal transfer rate × km → booking CC; credit owner dept | No cross-charge (loses incentive to lend) |
| Shared ride | Trip cost split **in proportion to each rider's solo (direct) distance** | Equal split (simple but unfair to short hops) |
| Shuttle / commute bus (fixed monthly contract) | **Option A:** monthly cost ÷ total boardings × dept boardings (needs boarding scan) | **Option B:** headcount-based fixed allocation (no tracking needed, less fair) |
| Visitor ride | As above → host's CC/project | — |

Decision needed from Finance (Q4).

---

## 8. Architecture

### 8.1 Options considered

**Option A — Modular monolith (recommended)**
- One deployable backend with strict internal modules (§5), one PostgreSQL.
- Pros: fast delivery, simple ops, transactional booking/dispatch/costing, cheap.
- Cons: needs module-boundary discipline; whole app redeploys.
- When: single enterprise / a few plants; team < ~15 engineers. **This case.**

**Option B — Microservices + event bus (Kafka)**
- Pros: independent scaling/teams; good for multi-tenant SaaS at large scale.
- Cons: 3–5× ops cost, distributed transactions across booking/dispatch/billing.
- When: selling as SaaS to many plants with high GPS volume. Revisit later;
  module boundaries in A keep this door open.

Scale sanity check (assumption, to validate): ~500 vehicles × 1 GPS fix / 10 s
= **50 writes/s**; peak bookings a few hundred/min. A single Postgres handles
this comfortably.

### 8.2 Component view

```
 Employee App (RN/Flutter) ─┐
 Driver App (Android)  ─────┤        ┌──────────────────────────────────────────┐
 Web: Control room / Admin ─┼─HTTPS─►│ API (REST) + WebSocket/SSE               │
 Vendor & Finance portal ───┤        │  Modular monolith                        │
 Visitor tracking link ─────┘        │  ┌─────────┐┌────────┐┌───────────────┐  │
                                     │  │Org/Policy││ Fleet  ││ Network/Route │  │
 GPS devices ──TCP/UDP──► Traccar ───┼─►│Tracking ││Booking ││ Dispatch      │  │
                       (protocol     │  │Visitor  ││Billing ││ Notification  │  │
                        decoding)    │  └─────────┘└────────┘└───────────────┘  │
                                     └───┬───────────┬──────────┬───────────────┘
                                         │           │          │
                         PostgreSQL+PostGIS   Redis (live pos,   OSRM/GraphHopper
                         (+ partitioned       pub/sub, locks)    on plant OSM graph
                          position history)
 Integrations: Azure AD/SSO (OIDC) · HRMS (employees, dept, grade) · SAP (CC, WBS, postings)
               · VMS / gate system · SMS/WhatsApp gateway · FCM
```

### 8.3 Technology choices (tradeoffs)

| Concern | Recommendation | Alternative | Note |
|---|---|---|---|
| Backend | Java/Kotlin Spring Boot | TypeScript NestJS | Pick what the plant IT team can maintain long-term |
| DB | PostgreSQL + PostGIS | — | Geo queries, transactions, partitioning |
| Live state | Redis | In-DB only | Latest positions, dispatch locks, pub/sub |
| GPS ingest | **Traccar** (self-hosted) | Vendor cloud APIs | Avoid writing device protocol parsers |
| Routing | OSRM or GraphHopper on custom plant OSM | Google Maps | Internal roads; confidentiality |
| Maps | Self-hosted tiles (OpenMapTiles) / plant CAD overlay | Google/Mapbox | Same |
| Mobile | Flutter or React Native (one codebase) | Native | Driver app Android-only |
| Web | React | — | Control room, admin, portals |
| Auth | OIDC with corporate IdP; OTP for drivers/visitors | Own user store | No password management |
| Hosting | As per IT policy: on-prem K8s or private cloud | — | Q6 |

### 8.4 Cross-cutting
- **Concurrency:** dispatch assignment and seat holds via row-level locks /
  conditional updates; idempotency keys on all booking & payment-like APIs.
- **Offline:** driver app queues events (pickup/drop) and replays; GPS devices
  buffer and forward; employee app caches stops/timetables.
- **Observability:** structured logs with booking/trip IDs; metrics: GPS fix
  lag per vehicle, dispatch time, unfulfilled requests, ETA error, SAP posting
  failures; alerts on device-offline and dispatch backlog.
- **Security & privacy:** track vehicles, not people; rider location used
  on-device for stop search only; RBAC per persona; audit log for approvals,
  overrides and billing changes; DPDP Act 2023 retention policy for position
  history (e.g. 90 days raw, aggregates longer — confirm with Legal).
- **Failure recovery:** if dispatch engine fails, control room can assign
  manually; if GPS missing, trip costed from driver-app odometer/fallback and
  flagged for review; SAP posting via outbox table with retries.

---

## 9. Core data model (sketch)

```
Department(id, name, parent_id)            CostCenter(id, code, dept_id, owner_emp_id, monthly_budget)
Project(id, wbs_code, cost_center_id)      Employee(id, sso_id, dept_id, grade, default_cc_id, manager_id)
Policy(id, grade_band, ride_type, purpose, window, action)

Vendor(id, name)                           Contract(id, vendor_id, rate_card_json, valid_from, valid_to)
Vehicle(id, reg_no, type, capacity, owner_type, owner_id, mode, gps_device_id, status)
Driver(id, vendor_id, name, phone, licence_no)   ComplianceDoc(id, subject_type, subject_id, doc_type, expiry)
Duty(id, vehicle_id, driver_id, start_at, end_at)

Stop(id, name, geom, geofence)             Route(id, name)   RoutePattern(route_id, seq, stop_id)
Trip(id, route_id, service_date, vehicle_id)   StopTime(trip_id, seq, sched_arr, sched_dep, actual_arr, actual_dep)

Position(vehicle_id, ts, geom, speed, heading)   -- partitioned by day
SafetyEvent(id, vehicle_id, type, ts, geom, ack_by)

Visit(id, gate_pass_ref, host_emp_id, cc_id, project_id, from, to)   Visitor(id, visit_id, name, phone)
RideRequest(id, requester_emp_id, type[EXCLUSIVE|SHARED], when, pickup, drop, pax_count,
            purpose, cc_id, project_id, visit_id, status, idempotency_key)
RidePassenger(ride_request_id, employee_id | visitor_id, otp)
Approval(id, ride_request_id, approver_id, status, ts)
Job(id, vehicle_id, driver_id, status)     JobStop(job_id, seq, ride_request_id, kind[PICKUP|DROP], eta, actual)
SeatBooking(id, trip_id, employee_id, from_seq, to_seq, status)   Boarding(trip_id, employee_id, stop_id, ts)

TripCost(id, job_id|trip_id, km, minutes, amount, rate_card_ref)
CostAllocation(trip_cost_id, cc_id, project_id, amount, basis)
VendorStatement(id, vendor_id, period, status)   SapPosting(id, period, payload, status, attempts)  -- outbox
```

---

## 10. Phased delivery

### Phase 0 — Discovery (3–4 weeks) — *do not skip*
- Fleet inventory (owned vs contract, per department), existing contracts & rate cards.
- Current trip volumes (logbooks / duty slips), peak patterns, complaints.
- Digitise plant map: roads, gates, stops, restricted zones, speed limits.
- Confirm GPS device models/protocols; check that Traccar supports them.
- Policies: entitlements, approvals, cost allocation (Finance sign-off).
- Integrations available: SSO, HRMS, SAP, VMS.

### Phase 1 — MVP "Visibility + On-demand + Costing" (~3–4 months)
- Org/CC sync, Fleet & vendor master with compliance expiry.
- GPS ingestion, live map, safety alerts (over-speed, geofence, offline).
- Shuttle/commute: stops, routes, live next-arrival (no seat booking).
- On-demand **exclusive** rides (now + scheduled), approval policy,
  dispatch (auto + manual), driver app, OTP.
- Book-for-visitor with SMS link (gate pass ref captured as text if no VMS integration yet).
- Trip costing → cost center; monthly reports; CSV export to Finance.
- **Pilot:** 2–3 departments + 1 shuttle route + 1 vendor.

### Phase 2 — Efficiency (~3 months)
- Shared rides (pooling heuristic).
- Seat reservation on peak-flagged shuttle/commute trips; boarding scan.
- Vendor portal, statements & dispute flow, GPS-vs-claim reconciliation.
- SAP posting integration; VMS/gate integration.
- Department vehicle lending to pool; cross-charge.

### Phase 3 — Optimisation (as data justifies)
- Commute route optimisation from employee home stops & shifts.
- Calendar (Outlook) integration: "book ride for this meeting".
- Demand forecasting for fleet sizing; EV charging scheduling.

### Release safety
- Feature flags per module and per department (pilot gating).
- Run in parallel with the existing manual process during pilot; keep the
  manual process as rollback for 1–2 billing cycles.
- DB migrations backward compatible; blue/green deploys.

### Test plan
- Unit: policy evaluation, cost allocation splits, segment-capacity math, ETA.
- Integration: Traccar → tracking pipeline with recorded GPS traces; SAP outbox.
- Concurrency: parallel dispatch of the same vehicle; last-seat booking race.
- Simulation: replay a recorded day of GPS + synthetic ride requests to measure
  wait time & utilisation before go-live.
- Field UAT with real drivers (low-literacy UX, Hindi, poor network).

---

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Scope creep (5 products) | High | High | Phase gates; pilot scope fixed |
| Contract drivers don't use the app | High | High | Simple Hindi UI, vendor SLA clause, dispatcher fallback, incentives |
| Departments refuse pooling | High | Medium | Show utilisation data; management mandate; lending with cross-charge credit |
| GPS device heterogeneity / poor data | Medium | High | Traccar; device certification list in vendor contracts |
| Plant map inaccurate | Medium | High | Phase 0 digitisation; editable map in admin |
| Abuse of free exclusive rides | Medium | Medium | Policy + approvals + budget visibility per CC |
| Finance cannot agree allocation rules | Medium | Medium | Decide in Phase 0; configurable allocation basis |
| Privacy / union concerns about tracking | Medium | Medium | Track vehicles only; clear retention policy |
| Connectivity dead zones | Medium | Medium | Offline queues; geofence-based auto events |

---

## 12. Open questions (blocking)

1. **Gate pass / VMS:** does a system exist? Integrate or build? Who owns it (Security)?
2. **Cab drivers:** can they be mandated to use a smartphone driver app? (GPS device alone cannot do on-demand.)
3. **Scale:** number of vehicles (owned / contract), employees, daily intra-plant trips, visitors/day.
4. **Cost allocation:** Finance's preferred basis for shuttles (boardings vs headcount) and shared rides; cross-charge for lending dept vehicles?
5. **Entitlement policy:** who may book exclusive rides; approval thresholds.
6. **Hosting & IT:** on-prem vs cloud; SSO provider; SAP version/interface (IDoc, BAPI, OData); preferred stack.
7. **Product intent:** internal tool for one plant, or a product for multiple plants (multi-tenancy)?
8. **Build vs buy:** has MoveInSync/Routematic been evaluated? Plant-specific gaps justify build, but this should be a conscious decision.
