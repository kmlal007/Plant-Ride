# Pilot demo walkthrough: Demo Steel Works

A scripted customer showcase, about 45 minutes. It runs on the fictional pilot plant
**Demo Steel Works (Pilot)**, which ships with:
- 11 departments and their cost centers, 3 projects
- about 150 employees (15 can log in)
- 7 drivers and 2 vendors
- a mixed fleet: shuttles, a commute bus, vendor cars and SUVs, a pool SUV, two department-owned vehicles
- 3 timetabled routes
- 30 days of ride history

A built-in **simulator** makes the plant feel live. Shuttles move along their timetable, cabs drive to
pickups and drops on simulated GPS, and "bot" drivers handle rides you don't drive yourself.

> Everything shown is real application behaviour. Only the GPS devices and the drivers other than yours are
> simulated.

## 1. Before the meeting (10 minutes)

```bash
# Fresh start the morning of the demo: history is generated relative to "today".
docker compose --env-file .env.demo --profile demo down -v
docker compose --env-file .env.demo --profile demo up -d --build
```

Open three browser windows side by side. Use a narrow window or the browser's phone emulation for the two apps.

| Window | URL | Sign in as |
|---|---|---|
| Admin / control room | http://localhost:8081 | `demo.admin` / `password` |
| Employee app (browser build) | http://localhost:8082 | `E301` / `password` (Neha Gupta, General Office) |
| Driver app (browser build) | http://localhost:8083 | `9000000001` / `password` (Suresh Yadav, car JH05TC3001) |

To show the real mobile apps on phones instead, run `npx expo start` in `user-app/` and `driver-app/` and open them in
Expo Go. Set `EXPO_PUBLIC_API_URL` to the laptop's LAN address first.

Checklist:
- Shuttles run 06:00–22:00 plant time (IST). Outside those hours there are no live shuttle positions.
- The admin map needs internet access for map tiles. Without it, vehicles still show on a blank background.
- Push notifications are switched off in demo mode; the apps refresh every few seconds instead.

### Demo logins (all passwords: `password`)

| Login | Who | Use it to show |
|---|---|---|
| `demo.admin` | Transport admin | Configuration, control room, reports |
| `CR01` | Control room desk (dispatcher) | Live operations without configuration rights |
| `E301` | Neha Gupta, General Office | Shuttles, booking, a scheduled delegate ride |
| `E101` | Ravi Kumar, Blast Furnace | Has an exclusive SUV request waiting for approval |
| `E102` | Sunita Das, Blast Furnace | Department vehicle priority |
| `M100` | Rajesh Sinha, Head of Blast Furnace | Approver for Ravi and Sunita |
| `M200`, `M300` | Department heads (SMS, Administration) | Other approvers |
| `9000000001` | Suresh Yadav, vendor driver | **Your** driver: never operated by the simulator |
| `9000000002`–`7` | Other drivers | Operated by the simulator (bots) |
| `admin` / `admin123` | Installation admin | Multi-plant: owns the separate "Main Plant" |

## 2. The story

### Act 1 – "Today nobody can see the fleet" (control room, 5 min)
*Admin window → Control room*

- Live map:
  - **purple** = shuttles and the commute bus, moving on their timetable
  - **green** = cabs available
  - **blue** = cabs on a trip
- Top tiles: rides waiting for a vehicle, unfulfilled rides, rides on trip, completed today, available vehicles, open safety alerts.
- **Safety alerts**: over-speed events against the plant speed limit (40 km/h in this plant), detected from GPS.
- **Fleet** table: every vehicle with status and age of its last GPS fix. Shuttles say *tracked only* because they're never dispatched.

Message: *one screen for department, vendor and pool vehicles. Today this information sits in log books and phone calls.*

### Act 2 – "When is my shuttle?" (employee app, 5 min)
*Employee window as Neha → Shuttles*

- **Stops near you**, with walking time. On a laptop outside the plant, the app says so and uses the plant centre.
- **Next at Main Gate**:
  - **live** arrivals come from the shuttle's GPS ("Arriving", "18 min")
  - **scheduled** times come from the metro-style timetable (Iron Zone Loop every 15 min)
  - the shift bus from the township also appears
- **Where are you going?** → tap *Blast Furnace*: direct options ranked by arrival time ("walk 12 min, board S1 at 10:30, arrive 10:52").

Message: *fixed routes work like a metro; the app gives every stop the next shuttle and the closest stop.*

### Act 3 – On-demand ride, end to end (employee + driver apps, 10 min)
*Employee window → Book ride*

1. Shared seat · Pickup **General Office** · Drop **Blast Furnace** · Now · purpose "Production planning meeting".
   Charge to: CC1000 (Neha's department, preselected); a project could be chosen instead. **Book ride**.
2. The confirmation shows the **OTP**. *My rides* shows "Waiting for driver to accept" with the car and driver.
3. *Driver window*: a **NEW RIDE** card with a countdown, pickup, drop and passenger count. **Accept**.
   - Ignoring the offer passes the ride to the next nearest driver after 45 s. You can show that, and the configurable timeout.
4. Suresh's car is already at the General Office. **I have arrived** → type Neha's **OTP** → **Start trip**.
   A wrong OTP is rejected: this proves the right person boarded.
5. Switch to the *Admin window*: the car turns **blue (ON_TRIP)** and moves towards the Blast Furnace on the map.
   It takes about 4 minutes at the simulated speed; use the time for Act 4.
6. *Driver window* → **Complete trip**. The driver sees "2.2 km (GPS) · 4 min". Neha's history shows the distance, time
   and **amount charged to her cost center**.

Message: *Rapido-style booking without payment. The ride is charged to the department, and the distance comes
from GPS, not a handwritten duty slip.*

### Act 4 – Approval for an exclusive vehicle (5 min)
*Employee window → sign out → sign in as `M100` (Rajesh) → Approvals*

- Ravi has requested an **exclusive SUV for 4 people** for a "Hot metal logistics review with SMS".
- **Approve**. The ride is dispatched straight away. A simulator-driven SUV accepts it, drives to the Blast Furnace, starts with
  the OTP and completes it. Follow it in *Admin → Rides*: it moves from OFFERED to ACCEPTED, DRIVER_ARRIVED, IN_PROGRESS and COMPLETED.
- Policy: exclusive rides need the manager's approval; shared seats don't. This is a per-plant setting.

### Act 5 – Department-owned vehicles (5 min)
*Employee window as `E102` (Sunita, Blast Furnace) → Book ride: Blast Furnace → LD Steel Melt Shop, vehicle **Car***

- The Blast Furnace's **own car** (JH05BF6001, bot driver) is offered first, ahead of nearer vendor cars.
- When it's idle, the same car is **lent** to other departments. The owning department gets a credit and the borrower is charged.
- *Admin → Plants & settings → Cost policies* shows the four configurable choices:
  1. Department vehicles: own department only, or lent when idle
  2. A department using its own vehicle: charge it at the rate card, or record the ride at zero
  3. A department borrowing a lent vehicle: charge the borrower and credit the owner, charge the borrower only, or no charge
  4. Shuttle and bus monthly cost: central overhead, one central cost center, or split by headcount

Message: *Finance hasn't decided on the policy yet. The product doesn't force a decision; each choice is a setting.*

### Act 6 – Visitors and delegates (optional module, 3 min)
*Admin → Rides (today)*

- Neha's **scheduled** ride for "Mr. K. Tanaka (Delegate)" from Main Gate to General Office:
  - gate pass reference GP-2026-0412
  - charged to project **WBS-DIGITAL**, not a cost center
  - the delegate receives the OTP by SMS (an SMS gateway is configurable)
- Booking for a visitor: *Employee app → Book ride →* "This ride is for a visitor / delegate".
- The module is switched on per plant. The plant's own gate-pass system remains the system of record.

### Act 7 – Control room exceptions (3 min)
*Admin → Rides*, with status **UNFULFILLED** and the last 30 days selected

- Rides that found no vehicle within the search timeout are flagged here. The desk can **Assign** an available vehicle manually or **Cancel** with a reason.
- Driver no-shows and cancellations are recorded with reasons, for vendor and employee follow-up.

### Act 8 – Finance (5 min)
*Admin → Cost reports*, range: **the whole previous month** (e.g. 1–30 September)

- Charges per cost center, with budget usage. **Export CSV** gives Finance a file until the SAP integration exists.
- The amounts include ride fares, credits for lent department vehicles (negative rows) and the **posted** shuttle
  allocation for that month, split by headcount. The demo posts the previous month automatically.
- **Shuttle & bus cost allocation** for the current month:
  - preview of route running costs (₹5.7 lakh/month) split by headcount
  - **Post allocation** posts it once; a second post is refused, so Finance never double-books

### Act 9 – Configuration and scale (5 min)
- **Routes**: timetable (first and last departure, every N minutes), stops with minutes after the first stop, monthly running cost.
- **Vehicles**: owner (pool, vendor or department), on-demand or fixed-route, GPS device id. **Rate cards**: base + per km + per minute, with a minimum.
- **Users**: employees with an approving manager and default cost center; drivers tied to vendors.
- **Plants & settings**: **multi-plant mode**. Sign in as `admin` / `admin123` (Main Plant) and use *Switch to* on Demo Steel Works; each plant's data is kept separate.

### Act 10 – Deployment and integrations (talking points)
- **On-premise by default**: Docker images run in the plant data centre. The same images run on any cloud with managed PostgreSQL. See `docs/DEPLOYMENT.md`.
- **Push notifications**: Firebase by default. An on-prem webhook gateway or none can be configured instead, and the apps fall back to polling.
- **GPS**: existing AIS-140 or vendor devices connect via Traccar, which supports 200+ device protocols.
- **Roadmap**: shared-ride pooling, shuttle seat reservation at shift peaks, SSO and HRMS sync, SAP posting, vendor portal with invoice reconciliation.

## 3. Resetting between demos

```bash
docker compose --env-file .env.demo --profile demo down -v && \
docker compose --env-file .env.demo --profile demo up -d --build
```

## 4. Troubleshooting

| Symptom | Fix |
|---|---|
| No live shuttles | Outside 06:00–22:00 IST, or the backend was just started (wait about 10 s). |
| Neha's ride isn't offered to Suresh | Suresh is busy or off duty. Check the driver window; another driver (bot) will take it instead. |
| Map is grey | No internet for map tiles. Set `MAP_TILE_URL` to a reachable or self-hosted tile server. |
| "Server is unavailable (502)" | The backend is (re)starting; wait for `docker compose ps` to show it healthy. |
