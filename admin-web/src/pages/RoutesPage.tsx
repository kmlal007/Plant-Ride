import { FormEvent, useState } from 'react';
import { api } from '../api';
import { useApi } from '../hooks';

interface Stop {
  id: number;
  code: string;
  name: string;
}

interface RouteStop {
  stopId: number;
  offsetMinutes: number;
}

interface RouteView {
  route: {
    id: number;
    code: string;
    name: string;
    routeKind: string;
    firstDeparture: string;
    lastDeparture: string;
    headwayMinutes: number;
    daysOfWeek: string;
    active: boolean;
    monthlyCost: number | null;
  };
  stops: (RouteStop & { seq: number })[];
}

const EMPTY = {
  code: '',
  name: '',
  routeKind: 'SHUTTLE',
  firstDeparture: '06:00',
  lastDeparture: '22:00',
  headwayMinutes: 15,
  daysOfWeek: '1,2,3,4,5,6,7',
  active: true,
  monthlyCost: null as number | null,
};

/** Routes are timetabled like a metro: a departure every N minutes between first and last trip. */
export function RoutesPage() {
  const routes = useApi<RouteView[]>('/api/admin/routes');
  const stops = useApi<Stop[]>('/api/admin/stops');
  const [form, setForm] = useState(EMPTY);
  const [pattern, setPattern] = useState<RouteStop[]>([]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const stopName = (id: number) => stops.data?.find((s) => s.id === id)?.name ?? `#${id}`;

  const edit = (r: RouteView) => {
    setEditingId(r.route.id);
    setForm({
      ...r.route,
      firstDeparture: r.route.firstDeparture.slice(0, 5),
      lastDeparture: r.route.lastDeparture.slice(0, 5),
    });
    setPattern(r.stops.map((s) => ({ stopId: s.stopId, offsetMinutes: s.offsetMinutes })));
    setError(null);
  };

  const reset = () => {
    setEditingId(null);
    setForm(EMPTY);
    setPattern([]);
    setError(null);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      const body = { ...form, headwayMinutes: Number(form.headwayMinutes), stops: pattern };
      if (editingId === null) await api('/api/admin/routes', { method: 'POST', body });
      else await api(`/api/admin/routes/${editingId}`, { method: 'PUT', body });
      reset();
      routes.reload();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const updateStop = (i: number, patch: Partial<RouteStop>) =>
    setPattern(pattern.map((p, idx) => (idx === i ? { ...p, ...patch } : p)));

  return (
    <div className="page">
      <h1>Routes &amp; Timetables</h1>
      <p className="muted">
        Shuttles and buses run on fixed routes. Riders see the next arrivals at each stop, computed from the headway
        and each stop’s minutes after the first stop. Live GPS estimates are shown alongside when a vehicle is
        assigned to the route.
      </p>
      <div className="split">
        <section className="card grow">
          {routes.error && <div className="error">{routes.error}</div>}
          <table>
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Kind</th>
                <th>Service</th>
                <th>Stops</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(routes.data ?? []).map((r) => (
                <tr key={r.route.id} className={editingId === r.route.id ? 'selected' : ''}>
                  <td>{r.route.code}</td>
                  <td>
                    {r.route.name} {!r.route.active && <span className="badge">inactive</span>}
                  </td>
                  <td>{r.route.routeKind}</td>
                  <td>
                    {r.route.firstDeparture.slice(0, 5)}–{r.route.lastDeparture.slice(0, 5)} every{' '}
                    {r.route.headwayMinutes} min
                    {r.route.monthlyCost !== null && (
                      <div className="muted">₹{Number(r.route.monthlyCost).toLocaleString('en-IN')}/month</div>
                    )}
                  </td>
                  <td>{r.stops.map((s) => `${stopName(s.stopId)} (+${s.offsetMinutes})`).join(' → ')}</td>
                  <td>
                    <button className="link" onClick={() => edit(r)}>
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
        <section className="card form-card">
          <h2>{editingId === null ? 'New route' : `Edit #${editingId}`}</h2>
          <form onSubmit={submit}>
            <label>
              <span>Code *</span>
              <input value={form.code} required onChange={(e) => setForm({ ...form, code: e.target.value })} />
            </label>
            <label>
              <span>Name *</span>
              <input value={form.name} required onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </label>
            <label>
              <span>Kind</span>
              <select value={form.routeKind} onChange={(e) => setForm({ ...form, routeKind: e.target.value })}>
                <option value="SHUTTLE">Intra-plant shuttle</option>
                <option value="COMMUTE">Commute bus</option>
              </select>
            </label>
            <div className="row">
              <label>
                <span>First departure</span>
                <input
                  type="time"
                  value={form.firstDeparture}
                  onChange={(e) => setForm({ ...form, firstDeparture: e.target.value })}
                />
              </label>
              <label>
                <span>Last departure</span>
                <input
                  type="time"
                  value={form.lastDeparture}
                  onChange={(e) => setForm({ ...form, lastDeparture: e.target.value })}
                />
              </label>
            </div>
            <div className="row">
              <label>
                <span>Every (min)</span>
                <input
                  type="number"
                  min={1}
                  value={form.headwayMinutes}
                  onChange={(e) => setForm({ ...form, headwayMinutes: Number(e.target.value) })}
                />
              </label>
              <label>
                <span>Days (1=Mon … 7=Sun)</span>
                <input value={form.daysOfWeek} onChange={(e) => setForm({ ...form, daysOfWeek: e.target.value })} />
              </label>
            </div>
            <label>
              <span>Monthly running cost (₹)</span>
              <input
                type="number"
                min={0}
                value={form.monthlyCost ?? ''}
                onChange={(e) => setForm({ ...form, monthlyCost: e.target.value === '' ? null : Number(e.target.value) })}
              />
              <small className="muted">Distributed to departments per the plant’s shuttle cost policy.</small>
            </label>
            <label className="checkbox">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm({ ...form, active: e.target.checked })}
              />
              Active
            </label>
            <h3>Stops in order</h3>
            {pattern.map((p, i) => (
              <div className="row" key={i}>
                <select value={p.stopId} onChange={(e) => updateStop(i, { stopId: Number(e.target.value) })}>
                  {(stops.data ?? []).map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.code} — {s.name}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  min={0}
                  title="Minutes after the first stop"
                  value={p.offsetMinutes}
                  onChange={(e) => updateStop(i, { offsetMinutes: Number(e.target.value) })}
                />
                <button type="button" className="link" onClick={() => setPattern(pattern.filter((_, idx) => idx !== i))}>
                  Remove
                </button>
              </div>
            ))}
            <button
              type="button"
              className="secondary"
              disabled={!stops.data?.length}
              onClick={() =>
                setPattern([
                  ...pattern,
                  {
                    stopId: stops.data![0].id,
                    offsetMinutes: pattern.length ? pattern[pattern.length - 1].offsetMinutes + 5 : 0,
                  },
                ])
              }
            >
              + Add stop
            </button>
            <small className="muted">Second column: minutes after the trip leaves the first stop.</small>
            {error && <div className="error">{error}</div>}
            <div className="actions">
              <button type="submit">{editingId === null ? 'Create' : 'Save'}</button>
              {editingId !== null && (
                <button type="button" className="secondary" onClick={reset}>
                  Cancel
                </button>
              )}
            </div>
          </form>
        </section>
      </div>
    </div>
  );
}
