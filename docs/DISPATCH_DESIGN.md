# Design: matching ride bookings to drivers (dispatch)

| | |
|---|---|
| Status | **Implemented (v1)**; improvements proposed in §9 |
| Scope | On-demand rides (exclusive and shared). Fixed-route shuttles and buses are timetabled, not dispatched. |
| Code | `backend/src/main/java/com/plantride/ride/` (`DispatchService`, `RideService`, `RideSweeper`), `fleet/VehicleRepository` |
| Tests | `RideFlowIntegrationTest`, `CostPolicyIntegrationTest`, `PilotDemoIntegrationTest` |

## 1. Problem

When an employee books a ride, find a suitable vehicle with a signed-on driver, get that driver's commitment
quickly, and never give one vehicle to two rides. When no vehicle is available, keep trying for a bounded time,
then hand the ride to the transport control room instead of failing silently.

### Requirements

| # | Requirement |
|---|---|
| R1 | Only dispatch vehicles that can do the job: on duty, enough seats, the requested type, a known recent position, close enough. |
| R2 | Respect ownership and cost policy: department vehicles serve their own department first and are lent to others only if the plant allows it. |
| R3 | Prefer the best vehicle: own department, then pool or vendor, then lent; nearest first within each group. |
| R4 | The driver must explicitly accept. An unanswered offer must not block the ride. |
| R5 | No double booking of a vehicle, even under concurrent requests. |
| R6 | A driver who declined is not offered the same ride again. |
| R7 | Scheduled rides are dispatched shortly before pickup, not at booking time. |
| R8 | Bounded search: after a timeout the ride becomes `UNFULFILLED` and visible to the control room for manual assignment. |
| R9 | Every timing and radius is configurable per plant. |

### Non-goals (v1)

- Pooling several shared riders into one vehicle (Phase 2).
- Routing over the plant road network (straight-line distance in v1; see §9).
- Driver earnings optimisation (drivers are salaried or contracted, not paid per trip).

## 2. Terms

| Term | Meaning |
|---|---|
| **Candidate** | A vehicle that passes every eligibility filter for a ride (§4). |
| **Claim** | The atomic database update that reserves a vehicle (`AVAILABLE → ASSIGNED`). |
| **Offer** | A claimed vehicle's driver is asked to accept the ride before `offerExpiresAt`. |
| **Sweeper** | A background job that drives the time-based transitions (expiry, scheduling, retries, timeouts). |

## 3. Overview

**Sequential offers.** The ride is offered to exactly one driver at a time. If that driver declines or doesn't
respond, it moves to the next best candidate.

```
           ┌──────────────────────── RideService ─────────────────────────┐
 booking ──► create / approve ──► release() ──► SCHEDULED (if pickup is later)
           │                          │
           │                          └──► startSearch() ──► SEARCHING
           │                                                      │
           │                     DispatchService.dispatch(ride) ◄─┘◄── RideSweeper (every 10 s)
           │                          │ rank candidates → claim first free vehicle
           │                          ▼
           │                       OFFERED ──► driver app: accept / decline
           └──────────────────────────────────────────────────────────────┘
```

## 4. Eligibility filters (`DispatchService.rankCandidates`)

Input: vehicles of the ride's plant with `serviceMode = ON_DEMAND`, `active = true`, `status = AVAILABLE`.

| # | Filter | Rule | Requirement |
|---|---|---|---|
| F1 | Driver on duty | `currentDriverId != null` | R1 |
| F2 | Not declined | Driver isn't in `ride.rejectedDriverIds` | R6 |
| F3 | Seats | `capacity ≥ passengerCount` | R1 |
| F4 | Type | Ride's `vehicleType` is null (any) or equals the vehicle type | R1 |
| F5 | Ownership | Not department-owned, **or** owned by the rider's department, **or** the plant's `departmentVehicleSharing = LEND_WHEN_IDLE` | R2 |
| F6 | Fresh position | `lastFixAt` within the last **10 min** (`MAX_FIX_AGE`) | R1 |
| F7 | Distance | Straight-line distance to pickup ≤ plant `dispatchRadiusKm` | R1 |

## 5. Ranking

Candidates are sorted by:

1. **Ownership preference** (`preference()`):
   - `0`: the rider's own department's vehicle
   - `1`: pool or vendor vehicle
   - `2`: another department's lent vehicle
2. **Distance to pickup**, ascending (haversine; see §9 for drive time).

Why ownership first: a department paid for its vehicle, so it gets first use. Lent vehicles come last so the
lending department keeps them available whenever possible (R2, R3).

## 6. Claim and offer (no double booking)

```java
for (Vehicle v : rankCandidates(...)) {
    if (vehicles.transition(v.getId(), AVAILABLE, ASSIGNED) == 1) {   // atomic claim
        offer(ride, v, plant);                                          // OFFERED, expiry, notify
        return true;
    }
}
return false;                                                           // stay SEARCHING
```

`transition` is a single conditional statement:

```sql
UPDATE vehicle SET status = :to WHERE id = :id AND status = :from
```

- Only one transaction can match the `WHERE` clause, so two concurrent bookings can never claim the same vehicle (R5).
  The loser simply tries its next candidate.
- `Vehicle` uses dynamic updates, so GPS position updates arriving at the same time never overwrite `status`.
- `offer()` sets `vehicleId`, `driverId` and `offerExpiresAt = now + offerTimeoutSeconds`, then sends a push notification
  after the transaction commits.

## 7. Lifecycle after the offer

```
SEARCHING ──claim──► OFFERED ──accept──► ACCEPTED ──arrive──► DRIVER_ARRIVED ──OTP──► IN_PROGRESS ──► COMPLETED
    ▲                   │                     │                      │
    │  decline / expiry │                     │ driver gives up      │ 5 min wait → NO_SHOW
    └───────────────────┴─────────────────────┘
SEARCHING ── searchTimeoutMinutes elapsed ──► UNFULFILLED ── control room: assign / cancel
```

| Event | Behaviour | Code |
|---|---|---|
| Accept | Only the offered driver, only before expiry | `RideService.accept` |
| Decline (OFFERED or ACCEPTED) | Release the vehicle (`ASSIGNED → AVAILABLE`), add the driver to the declined list, clear the vehicle and driver, re-dispatch immediately | `RideService.decline` → `redispatch` |
| Offer expiry | The sweeper treats it as a decline | `RideService.sweep`, case OFFERED |
| Scheduled ride due | When `now ≥ scheduledAt − scheduledDispatchLeadMinutes`, start searching | `sweep`, case SCHEDULED (R7) |
| Still searching | Retry dispatch on every sweep; after `searchTimeoutMinutes` mark the ride `UNFULFILLED` and notify the rider | `sweep`, case SEARCHING (R8) |
| Manual assignment | The control room picks a vehicle; skips ranking but still uses the atomic claim and still needs driver acceptance | `DispatchService.assignManually` |
| Cancel | Releases a held vehicle; vehicle and driver are kept on the ride for audit | `RideService.cancel` |

**Concurrency between driver actions and the sweeper.** `RideRequest` has an `@Version` column. If a driver
accepts at the same moment the sweeper expires the offer, one transaction fails with an optimistic-lock conflict.
The driver sees "please retry" or the sweeper skips that ride; the state stays consistent.

**Sweeper isolation.** Each ride is processed in its own transaction (`TransactionTemplate`), so one failing ride
never blocks the others.

## 8. Configuration (per plant, *Admin → Plants & settings*)

| Setting | Default | Effect |
|---|---|---|
| `dispatchRadiusKm` | 10 | Filter F7 |
| `offerTimeoutSeconds` | 45 | How long a driver has to accept |
| `searchTimeoutMinutes` | 15 | Search time before `UNFULFILLED` |
| `scheduledDispatchLeadMinutes` | 15 | How early scheduled rides start searching |
| `departmentVehicleSharing` | `OWN_DEPARTMENT_ONLY` | Filter F5 |
| `exclusiveRideRequiresApproval` | true | Exclusive rides wait for manager approval before dispatch |

Installation-wide: `plantride.sweeper-interval-ms` (default 10 000 ms). Code constant: `MAX_FIX_AGE` = 10 min.

## 9. Known limitations and proposed improvements

| # | Limitation | Impact | Proposal | Effort |
|---|---|---|---|---|
| L1 | **Straight-line distance** | Inside a plant the nearest car "as the crow flies" may be behind a rail line, gate or one-way road, so the ranking is wrong. | Rank the top N (≈5) straight-line candidates by **drive time** from OSRM or GraphHopper running on the plant's road network (self-hosted, because the plant layout is confidential). Fall back to straight-line distance if routing is down. | M |
| L2 | **One driver at a time** | Worst case: offer timeout × number of declines before anyone accepts (45 s × 3 = over 2 min). | Lower the timeout to 20–30 s once push notifications are live. Optionally offer to the top 2–3 at once and give the ride to the first to accept, releasing the others. | S / M |
| L3 | **No fairness** | The nearest driver gets every ride while others idle, so vendor billing and driver workload become uneven. | Add a tie-breaker: when distances are within about 300 m, prefer the longest time since last trip. | S |
| L4 | **Rides matched one at a time** | At shift change, rides are matched in arrival order rather than the best overall pairing. | Every few seconds, match all waiting rides to available vehicles at once (Hungarian algorithm on drive time). Only worth it at high volume. | L |
| L5 | **Retries on a fixed interval** | Up to 10 s added delay when a vehicle frees up. | Re-dispatch waiting rides immediately when a vehicle becomes `AVAILABLE` (trip completed, driver signs on). | S |
| L6 | **No pooling** | A shared ride uses a whole vehicle. | Phase 2: add a shared ride to an active trip when the detour for existing riders is within a limit and seats allow. Needs a `Trip` entity with ordered pickups and drops. | L |
| L7 | **Scheduled rides use only a lead time** | During a peak the lead time can find no free vehicle. | Reserve capacity per time slot at booking; warn the booker when the slot is full. | M |
| L8 | **Multiple backend instances** | The sweeper runs on every instance (safe but duplicated work). | Add a scheduler lock (ShedLock) before scaling out. | S |

**Recommended next step: L1 + L2 + L5.** Together they deliver most of the gain in matching quality and pickup time for
a plant-sized fleet. L4 and L6 should wait for pilot data.

## 10. Observability (to add with the improvements)

| Metric | Why |
|---|---|
| Time to match (booking or approval → `ACCEPTED`), p50 / p95 | Main measure of dispatch quality |
| Offers per ride; decline and expiry rate per driver | Spots unresponsive drivers or an offer timeout that's too short |
| `UNFULFILLED` rate by hour and department | Fleet sizing and shift-peak shortage |
| Pickup ETA error (estimated vs actual arrival) | Validates the drive-time model (L1) |
| Trips per driver per shift (spread) | Fairness (L3) and vendor billing balance |

Structured logs already include the ride, vehicle and driver ids for offer, accept and expiry events.

## 11. Test plan

Existing tests (`mvn test`) cover:
- the full lifecycle with a GPS-measured fare
- offer expiry → next driver → decline → search timeout → manual assignment
- department-only vehicles, and lending when the plant allows it
- scheduled dispatch timing
- cancellation releasing the vehicle
- a simulated bot driver completing a ride end to end

Needed for the proposals:
- **L1:** unit tests with a stub routing service where drive-time order differs from straight-line order; fallback when routing is unavailable.
- **L2 (parallel offers):** a concurrency test where two drivers accept at once; exactly one wins and the other's vehicle is released.
- **L3:** deterministic tie-breaking given last-trip timestamps.
- **Load:** replay a shift-change burst (e.g. 200 bookings in 5 minutes, 30 vehicles) and record time to match and `UNFULFILLED` rate before and after each change.
