import 'leaflet/dist/leaflet.css';
import {
  Bus,
  CarFront,
  CircleCheck,
  Gauge,
  Hourglass,
  LayoutDashboard,
  LucideIcon,
  ShieldAlert,
  ShieldCheck,
  TriangleAlert,
  Truck,
} from 'lucide-react';
import { CircleMarker, MapContainer, TileLayer, Tooltip } from 'react-leaflet';
import { PageHeader } from '../components/PageHeader';
import { VEHICLE_COLOURS, VehicleStatusBadge } from '../components/StatusBadge';
import { ageLabel } from '../format';
import { useApi } from '../hooks';
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

const TILE_URL = import.meta.env.VITE_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

const LEGEND: [string, string][] = [
  ['Available', VEHICLE_COLOURS.AVAILABLE],
  ['Assigned', VEHICLE_COLOURS.ASSIGNED],
  ['On trip', VEHICLE_COLOURS.ON_TRIP],
  ['Off duty', VEHICLE_COLOURS.OFF_DUTY],
  ['Shuttle / bus', VEHICLE_COLOURS.FIXED_ROUTE],
];

function vehicleIcon(v: Vehicle): LucideIcon {
  if (v.vehicleType === 'BUS' || v.vehicleType === 'SHUTTLE') return Bus;
  if (v.vehicleType === 'SUV') return Truck;
  return CarFront;
}

export function DashboardPage() {
  const dash = useApi<Dashboard>('/api/admin/dashboard', 10_000);
  const vehicles = useApi<Vehicle[]>('/api/admin/vehicles', 10_000);
  const safety = useApi<SafetyEvent[]>('/api/admin/safety-events', 30_000);

  const tracked = (vehicles.data ?? []).filter((v) => v.active && v.lastLat !== null && v.lastLng !== null);
  const center: [number, number] = tracked.length ? [tracked[0].lastLat!, tracked[0].lastLng!] : [22.79, 86.19];
  const rides = dash.data?.ridesToday ?? {};
  const waiting = (rides.SEARCHING ?? 0) + (rides.OFFERED ?? 0) + (rides.PENDING_APPROVAL ?? 0);
  const regNo = (id: number) => (vehicles.data ?? []).find((v) => v.id === id)?.registrationNo ?? `#${id}`;

  return (
    <div className="page">
      <PageHeader
        icon={LayoutDashboard}
        title="Control room"
        description="Live fleet, ride pipeline and safety alerts. Refreshes every 10 seconds."
      />
      {dash.error && <div className="error">{dash.error}</div>}
      <div className="stats">
        <Stat icon={Hourglass} tone="warn" label="Waiting (approval / vehicle)" value={waiting} alert={waiting > 0} />
        <Stat icon={TriangleAlert} tone="danger" label="Unfulfilled today" value={rides.UNFULFILLED ?? 0} alert={(rides.UNFULFILLED ?? 0) > 0} />
        <Stat icon={CarFront} tone="accent" label="On trip now" value={rides.IN_PROGRESS ?? 0} />
        <Stat icon={CircleCheck} tone="ok" label="Completed today" value={rides.COMPLETED ?? 0} />
        <Stat icon={Gauge} tone="info" label="Vehicles available" value={dash.data?.vehicles.AVAILABLE ?? 0} />
        <Stat
          icon={ShieldAlert}
          tone="danger"
          label="Open safety alerts"
          value={dash.data?.openSafetyEvents ?? 0}
          alert={(dash.data?.openSafetyEvents ?? 0) > 0}
        />
      </div>

      <div className="split">
        <section className="card grow map-card">
          <div className="card-header">
            <h2>Live map</h2>
            <span className="muted">{tracked.length} vehicles reporting</span>
          </div>
          <MapContainer center={center} zoom={14} style={{ height: 440, marginTop: 12 }}>
            <TileLayer url={TILE_URL} attribution="&copy; map contributors" />
            {tracked.map((v) => (
              <CircleMarker
                key={v.id}
                center={[v.lastLat!, v.lastLng!]}
                radius={v.serviceMode === 'FIXED_ROUTE' ? 10 : 8}
                pathOptions={{
                  color: '#ffffff',
                  weight: 2,
                  fillColor: v.serviceMode === 'FIXED_ROUTE' ? VEHICLE_COLOURS.FIXED_ROUTE : VEHICLE_COLOURS[v.status] ?? '#64748b',
                  fillOpacity: 0.95,
                }}
              >
                <Tooltip>
                  <strong>{v.registrationNo}</strong> · {v.vehicleType}
                  <br />
                  {v.serviceMode === 'FIXED_ROUTE' ? 'Fixed route' : v.status.replace('_', ' ')} ·{' '}
                  {Math.round(v.lastSpeedKmh ?? 0)} km/h · {ageLabel(v.lastFixAt)}
                </Tooltip>
              </CircleMarker>
            ))}
          </MapContainer>
          <div className="legend">
            {LEGEND.map(([label, c]) => (
              <span key={label}>
                <i style={{ background: c }} />
                {label}
              </span>
            ))}
          </div>
        </section>
        <section className="card form-card">
          <div className="card-header">
            <h2>
              <ShieldAlert size={18} aria-hidden /> Safety alerts
            </h2>
          </div>
          <ul className="alert-list">
            {(safety.data ?? []).slice(0, 8).map((e) => (
              <li key={e.id}>
                <span className={`alert-icon ${e.acknowledged ? 'done' : ''}`}>
                  <Gauge aria-hidden />
                </span>
                <div style={{ flex: 1 }}>
                  <div className="cell-main">
                    Over-speed · {e.speedKmh ? `${Math.round(e.speedKmh)} km/h` : ''}
                  </div>
                  <div className="cell-sub">
                    {regNo(e.vehicleId)} · {ageLabel(e.occurredAt)}
                  </div>
                </div>
                {!e.acknowledged && <span className="badge danger">Open</span>}
              </li>
            ))}
            {safety.data?.length === 0 && (
              <li className="empty" style={{ display: 'block' }}>
                <ShieldCheck aria-hidden />
                No alerts
              </li>
            )}
          </ul>
        </section>
      </div>

      <section className="card">
        <div className="card-header">
          <h2>Fleet</h2>
          <span className="muted">{(vehicles.data ?? []).filter((v) => v.active).length} active vehicles</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>Vehicle</th>
              <th>Service</th>
              <th>Status</th>
              <th className="num">Speed</th>
              <th>Last GPS fix</th>
            </tr>
          </thead>
          <tbody>
            {(vehicles.data ?? [])
              .filter((v) => v.active)
              .map((v) => {
                const Icon = vehicleIcon(v);
                const stale = !v.lastFixAt || Date.now() - new Date(v.lastFixAt).getTime() > 10 * 60_000;
                return (
                  <tr key={v.id}>
                    <td>
                      <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                        <span className="alert-icon done" style={{ background: 'var(--surface-2)' }}>
                          <Icon aria-hidden />
                        </span>
                        <div>
                          <div className="cell-main">{v.registrationNo}</div>
                          <div className="cell-sub">
                            {v.vehicleType} · {v.capacity} seats
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>{v.serviceMode === 'FIXED_ROUTE' ? 'Fixed route' : 'On demand'}</td>
                    <td>
                      {v.serviceMode === 'FIXED_ROUTE' ? (
                        <span className="badge info">Tracked</span>
                      ) : (
                        <VehicleStatusBadge status={v.status} />
                      )}
                    </td>
                    <td className="num">{v.lastSpeedKmh === null ? '—' : `${Math.round(v.lastSpeedKmh)} km/h`}</td>
                    <td className={stale ? 'warn-text' : ''}>{ageLabel(v.lastFixAt)}</td>
                  </tr>
                );
              })}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function Stat({
  icon: Icon,
  tone,
  label,
  value,
  alert,
}: {
  icon: LucideIcon;
  tone: string;
  label: string;
  value: number;
  alert?: boolean;
}) {
  return (
    <div className={`stat ${alert ? 'alert' : ''}`}>
      <span className={`stat-icon ${tone}`}>
        <Icon aria-hidden />
      </span>
      <div>
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}
