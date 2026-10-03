import 'leaflet/dist/leaflet.css';
import { CircleMarker, MapContainer, TileLayer, Tooltip } from 'react-leaflet';
import { useApi } from '../hooks';
import { ageLabel } from '../format';
import { Vehicle } from '../types';

interface Dashboard {
  ridesToday: Record<string, number>;
  vehicles: Record<string, number>;
  openSafetyEvents: number;
}

interface SafetyEvent {
  id: number;
  vehicleId: number;
  eventType: string;
  speedKmh: number | null;
  occurredAt: string;
  acknowledged: boolean;
}

const TILE_URL = import.meta.env.VITE_TILE_URL ?? 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

const STATUS_COLOURS: Record<string, string> = {
  AVAILABLE: '#1a7f37',
  ASSIGNED: '#bf8700',
  ON_TRIP: '#0969da',
  OFF_DUTY: '#6e7781',
  BLOCKED: '#cf222e',
};

export function DashboardPage() {
  const dash = useApi<Dashboard>('/api/admin/dashboard', 10_000);
  const vehicles = useApi<Vehicle[]>('/api/admin/vehicles', 10_000);
  const safety = useApi<SafetyEvent[]>('/api/admin/safety-events', 30_000);

  const tracked = (vehicles.data ?? []).filter((v) => v.active && v.lastLat !== null && v.lastLng !== null);
  const center: [number, number] = tracked.length ? [tracked[0].lastLat!, tracked[0].lastLng!] : [22.79, 86.19];
  const rides = dash.data?.ridesToday ?? {};
  const waiting = (rides.SEARCHING ?? 0) + (rides.OFFERED ?? 0);

  return (
    <div className="page">
      <h1>Control room</h1>
      {dash.error && <div className="error">{dash.error}</div>}
      <div className="stats">
        <Stat label="Waiting for vehicle" value={waiting} warn={waiting > 0} />
        <Stat label="Unfulfilled today" value={rides.UNFULFILLED ?? 0} warn={(rides.UNFULFILLED ?? 0) > 0} />
        <Stat label="On trip" value={rides.IN_PROGRESS ?? 0} />
        <Stat label="Completed today" value={rides.COMPLETED ?? 0} />
        <Stat label="Vehicles available" value={dash.data?.vehicles.AVAILABLE ?? 0} />
        <Stat label="Open safety alerts" value={dash.data?.openSafetyEvents ?? 0} warn={(dash.data?.openSafetyEvents ?? 0) > 0} />
      </div>

      <div className="split">
        <section className="card grow map-card">
          <MapContainer center={center} zoom={14} style={{ height: 420 }}>
            <TileLayer url={TILE_URL} attribution="&copy; map contributors" />
            {tracked.map((v) => (
              <CircleMarker
                key={v.id}
                center={[v.lastLat!, v.lastLng!]}
                radius={8}
                pathOptions={{
                  color: v.serviceMode === 'FIXED_ROUTE' ? '#8250df' : STATUS_COLOURS[v.status] ?? '#333',
                  fillOpacity: 0.8,
                }}
              >
                <Tooltip>
                  {v.registrationNo} · {v.vehicleType} · {v.status}
                  <br />
                  {v.lastSpeedKmh ?? 0} km/h · {ageLabel(v.lastFixAt)}
                </Tooltip>
              </CircleMarker>
            ))}
          </MapContainer>
          <div className="legend">
            {Object.entries(STATUS_COLOURS).map(([s, c]) => (
              <span key={s}>
                <i style={{ background: c }} /> {s}
              </span>
            ))}
            <span>
              <i style={{ background: '#8250df' }} /> SHUTTLE / BUS
            </span>
          </div>
        </section>
        <section className="card form-card">
          <h2>Safety alerts</h2>
          <table>
            <tbody>
              {(safety.data ?? []).slice(0, 10).map((e) => (
                <tr key={e.id}>
                  <td>{e.eventType}</td>
                  <td>
                    {(vehicles.data ?? []).find((v) => v.id === e.vehicleId)?.registrationNo ?? e.vehicleId}
                  </td>
                  <td>{e.speedKmh ? `${Math.round(e.speedKmh)} km/h` : ''}</td>
                  <td>{ageLabel(e.occurredAt)}</td>
                </tr>
              ))}
              {safety.data?.length === 0 && (
                <tr>
                  <td className="muted">No alerts.</td>
                </tr>
              )}
            </tbody>
          </table>
        </section>
      </div>

      <section className="card">
        <h2>Fleet</h2>
        <table>
          <thead>
            <tr>
              <th>Vehicle</th>
              <th>Type</th>
              <th>Service</th>
              <th>Status</th>
              <th>Last GPS fix</th>
            </tr>
          </thead>
          <tbody>
            {(vehicles.data ?? []).filter((v) => v.active).map((v) => (
              <tr key={v.id}>
                <td>{v.registrationNo}</td>
                <td>{v.vehicleType}</td>
                <td>{v.serviceMode}</td>
                <td>
                  {v.serviceMode === 'FIXED_ROUTE' ? (
                    <span className="muted">tracked only</span>
                  ) : (
                    <span className="badge" style={{ background: STATUS_COLOURS[v.status] }}>
                      {v.status}
                    </span>
                  )}
                </td>
                <td>{ageLabel(v.lastFixAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function Stat({ label, value, warn }: { label: string; value: number; warn?: boolean }) {
  return (
    <div className={`stat ${warn ? 'warn' : ''}`}>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}
