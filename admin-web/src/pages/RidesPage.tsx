import { useState } from 'react';
import { api } from '../api';
import { formatDateTime, formatMoney, localDate } from '../format';
import { useApi } from '../hooks';
import { Ride, Vehicle } from '../types';

const STATUSES = [
  'PENDING_APPROVAL', 'SCHEDULED', 'SEARCHING', 'OFFERED', 'ACCEPTED', 'DRIVER_ARRIVED', 'IN_PROGRESS',
  'COMPLETED', 'CANCELLED', 'REJECTED', 'NO_SHOW', 'UNFULFILLED',
];

const today = () => localDate();

export function RidesPage() {
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [status, setStatus] = useState('');
  const query = `/api/admin/rides?from=${from}&to=${to}${status ? `&status=${status}` : ''}`;
  const rides = useApi<Ride[]>(query, 10_000);
  const vehicles = useApi<Vehicle[]>('/api/admin/vehicles', 10_000);
  const [assigning, setAssigning] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const available = (vehicles.data ?? []).filter((v) => v.status === 'AVAILABLE' && v.serviceMode === 'ON_DEMAND');

  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
      rides.reload();
      vehicles.reload();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div className="page">
      <h1>Rides</h1>
      <div className="filters">
        <label>
          From <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label>
          To <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <label>
          Status
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
      </div>
      {(error || rides.error) && <div className="error">{error ?? rides.error}</div>}
      <section className="card">
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Created</th>
              <th>Rider</th>
              <th>Type</th>
              <th>From → To</th>
              <th>Status</th>
              <th>Vehicle / driver</th>
              <th>Km</th>
              <th>Charge</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {(rides.data ?? []).map((r) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                <td>
                  {formatDateTime(r.createdAt)}
                  {r.scheduledAt && <div className="muted">for {formatDateTime(r.scheduledAt)}</div>}
                </td>
                <td>
                  {r.requesterName}
                  {r.visitorName && <div className="muted">Visitor: {r.visitorName} {r.gatePassRef && `(${r.gatePassRef})`}</div>}
                </td>
                <td>
                  {r.rideType} · {r.passengerCount} pax
                </td>
                <td>
                  {r.pickupLabel} → {r.dropLabel}
                  {r.purpose && <div className="muted">{r.purpose}</div>}
                </td>
                <td>
                  <span className={`badge status-${r.status}`}>{r.status}</span>
                  {r.cancelReason && <div className="muted">{r.cancelReason}</div>}
                </td>
                <td>
                  {r.vehicleRegistrationNo ?? '—'}
                  {r.driverName && <div className="muted">{r.driverName}</div>}
                </td>
                <td>
                  {r.distanceKm ?? '—'}
                  {r.distanceSource === 'ESTIMATED' && <div className="muted">estimated</div>}
                </td>
                <td>{formatMoney(r.fare)}</td>
                <td className="nowrap">
                  {(r.status === 'SEARCHING' || r.status === 'UNFULFILLED') &&
                    (assigning === r.id ? (
                      <select
                        autoFocus
                        defaultValue=""
                        onChange={(e) =>
                          act(() =>
                            api(`/api/admin/rides/${r.id}/assign`, {
                              method: 'POST',
                              body: { vehicleId: Number(e.target.value) },
                            }),
                          ).then(() => setAssigning(null))
                        }
                        onBlur={() => setAssigning(null)}
                      >
                        <option value="" disabled>
                          Pick vehicle…
                        </option>
                        {available.map((v) => (
                          <option key={v.id} value={v.id}>
                            {v.registrationNo} ({v.vehicleType}, {v.capacity} seats)
                          </option>
                        ))}
                      </select>
                    ) : (
                      <button className="link" onClick={() => setAssigning(r.id)}>
                        Assign
                      </button>
                    ))}
                  {['PENDING_APPROVAL', 'SCHEDULED', 'SEARCHING', 'OFFERED', 'ACCEPTED', 'DRIVER_ARRIVED', 'UNFULFILLED'].includes(
                    r.status,
                  ) && (
                    <button
                      className="link danger"
                      onClick={() => {
                        const reason = window.prompt('Reason for cancelling?');
                        if (reason !== null) {
                          act(() => api(`/api/admin/rides/${r.id}/cancel`, { method: 'POST', body: { reason } }));
                        }
                      }}
                    >
                      Cancel
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {rides.data?.length === 0 && (
              <tr>
                <td colSpan={10} className="muted">
                  No rides in this period.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}
