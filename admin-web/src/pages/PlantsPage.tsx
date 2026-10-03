import { FormEvent, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { useApi } from '../hooks';

interface Plant {
  id: number;
  code: string;
  name: string;
  timezone: string;
  centerLat: number | null;
  centerLng: number | null;
  speedLimitKmh: number;
  dispatchRadiusKm: number;
  offerTimeoutSeconds: number;
  searchTimeoutMinutes: number;
  scheduledDispatchLeadMinutes: number;
  exclusiveRideRequiresApproval: boolean;
  visitorModuleEnabled: boolean;
  active: boolean;
  departmentVehicleSharing: string;
  ownVehicleChargeMode: string;
  lentVehicleChargeMode: string;
  shuttleCostAllocation: string;
  shuttleCentralCostCenterId: number | null;
}

/** Cost policies are configurable because Finance has not fixed them yet. */
const POLICIES: { key: keyof Plant; label: string; options: [string, string][] }[] = [
  {
    key: 'departmentVehicleSharing',
    label: 'Department-owned vehicles',
    options: [
      ['OWN_DEPARTMENT_ONLY', 'Serve own department only'],
      ['LEND_WHEN_IDLE', 'Own department first, lend to others when idle'],
    ],
  },
  {
    key: 'ownVehicleChargeMode',
    label: 'Department using its own vehicle',
    options: [
      ['CHARGE', 'Charge at rate card (full visibility)'],
      ['NO_CHARGE', 'Record at zero (cost already in budget)'],
    ],
  },
  {
    key: 'lentVehicleChargeMode',
    label: 'Department using a lent vehicle',
    options: [
      ['CHARGE_BOOKER_CREDIT_OWNER', 'Charge booker, credit owning department'],
      ['CHARGE_BOOKER_ONLY', 'Charge booker only'],
      ['NO_CHARGE', 'No charge'],
    ],
  },
  {
    key: 'shuttleCostAllocation',
    label: 'Shuttle & bus monthly cost',
    options: [
      ['NOT_ALLOCATED', 'Central overhead (not allocated)'],
      ['CENTRAL_COST_CENTER', 'Charge one cost center'],
      ['HEADCOUNT', 'Split by employee headcount'],
    ],
  },
];

const NEW_PLANT: Omit<Plant, 'id'> = {
  code: '',
  name: '',
  timezone: 'Asia/Kolkata',
  centerLat: null,
  centerLng: null,
  speedLimitKmh: 30,
  dispatchRadiusKm: 10,
  offerTimeoutSeconds: 45,
  searchTimeoutMinutes: 15,
  scheduledDispatchLeadMinutes: 15,
  exclusiveRideRequiresApproval: true,
  visitorModuleEnabled: false,
  active: true,
  departmentVehicleSharing: 'OWN_DEPARTMENT_ONLY',
  ownVehicleChargeMode: 'CHARGE',
  lentVehicleChargeMode: 'CHARGE_BOOKER_CREDIT_OWNER',
  shuttleCostAllocation: 'NOT_ALLOCATED',
  shuttleCentralCostCenterId: null,
};

const NUMBER_FIELDS: [keyof Plant, string, string][] = [
  ['speedLimitKmh', 'Speed limit (km/h)', 'GPS fixes above this raise an over-speed alert'],
  ['dispatchRadiusKm', 'Dispatch radius (km)', 'Only vehicles this close to the pickup are offered rides'],
  ['offerTimeoutSeconds', 'Driver offer timeout (s)', 'Then the ride moves to the next vehicle'],
  ['searchTimeoutMinutes', 'Search timeout (min)', 'Then the ride is flagged UNFULFILLED for the control room'],
  ['scheduledDispatchLeadMinutes', 'Scheduled dispatch lead (min)', 'Start looking for a vehicle this long before pickup'],
  ['centerLat', 'Map centre latitude', ''],
  ['centerLng', 'Map centre longitude', ''],
];

export function PlantsPage() {
  const { session, switchPlant } = useAuth();
  const plants = useApi<Plant[]>('/api/admin/plants');
  const settings = useApi<{ multiPlantEnabled: boolean }>('/api/admin/system-settings');
  const costCenters = useApi<{ id: number; code: string; name: string }[]>('/api/admin/cost-centers');
  const [form, setForm] = useState<Omit<Plant, 'id'> & { id?: number }>(NEW_PLANT);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const multi = settings.data?.multiPlantEnabled ?? false;

  const toggleMulti = async () => {
    setError(null);
    try {
      await api('/api/admin/system-settings', { method: 'PUT', body: { multiPlantEnabled: !multi } });
      settings.reload();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);
    try {
      if (form.id) await api(`/api/admin/plants/${form.id}`, { method: 'PUT', body: form });
      else await api('/api/admin/plants', { method: 'POST', body: form });
      setSaved(true);
      setForm(NEW_PLANT);
      plants.reload();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <div className="page">
      <h1>Plants &amp; settings</h1>
      <section className="card">
        <label className="checkbox">
          <input type="checkbox" checked={multi} onChange={toggleMulti} disabled={!settings.data} />
          Multi-plant mode
        </label>
        <p className="muted">
          When off, this installation serves a single plant. When on, admins can add plants and switch between them;
          all master data, fleet and rides are kept separate per plant.
        </p>
      </section>
      {error && <div className="error">{error}</div>}
      <div className="split">
        <section className="card grow">
          <table>
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Approval for exclusive</th>
                <th>Visitor module</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(plants.data ?? []).map((p) => (
                <tr key={p.id} className={p.id === session?.plantId ? 'selected' : ''}>
                  <td>{p.code}</td>
                  <td>
                    {p.name} {p.id === session?.plantId && <span className="badge">current</span>}
                  </td>
                  <td>{p.exclusiveRideRequiresApproval ? 'Yes' : 'No'}</td>
                  <td>{p.visitorModuleEnabled ? 'Enabled' : 'Off'}</td>
                  <td className="nowrap">
                    <button className="link" onClick={() => setForm(p)}>
                      Edit
                    </button>
                    {p.id !== session?.plantId && (
                      <button className="link" onClick={() => switchPlant(p.id).then(() => window.location.reload())}>
                        Switch to
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
        <section className="card form-card">
          <h2>{form.id ? `Edit ${form.code}` : 'New plant'}</h2>
          {!form.id && !multi && (plants.data?.length ?? 0) >= 1 && (
            <p className="muted">Enable multi-plant mode to add another plant.</p>
          )}
          <form onSubmit={submit}>
            <label>
              <span>Code *</span>
              <input
                value={form.code}
                required
                disabled={Boolean(form.id)}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
              />
            </label>
            <label>
              <span>Name *</span>
              <input value={form.name} required onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </label>
            <label>
              <span>Time zone</span>
              <input value={form.timezone} onChange={(e) => setForm({ ...form, timezone: e.target.value })} />
            </label>
            {NUMBER_FIELDS.map(([key, label, help]) => (
              <label key={key}>
                <span>{label}</span>
                <input
                  type="number"
                  step="any"
                  value={form[key] === null ? '' : String(form[key])}
                  onChange={(e) => setForm({ ...form, [key]: e.target.value === '' ? null : Number(e.target.value) })}
                />
                {help && <small className="muted">{help}</small>}
              </label>
            ))}
            <label className="checkbox">
              <input
                type="checkbox"
                checked={form.exclusiveRideRequiresApproval}
                onChange={(e) => setForm({ ...form, exclusiveRideRequiresApproval: e.target.checked })}
              />
              Exclusive rides need manager approval
            </label>
            <label className="checkbox">
              <input
                type="checkbox"
                checked={form.visitorModuleEnabled}
                onChange={(e) => setForm({ ...form, visitorModuleEnabled: e.target.checked })}
              />
              Visitor &amp; delegate module (optional)
            </label>
            <h3>Cost policies</h3>
            {POLICIES.map((p) => (
              <label key={p.key}>
                <span>{p.label}</span>
                <select value={String(form[p.key])} onChange={(e) => setForm({ ...form, [p.key]: e.target.value })}>
                  {p.options.map(([value, text]) => (
                    <option key={value} value={value}>
                      {text}
                    </option>
                  ))}
                </select>
              </label>
            ))}
            {form.shuttleCostAllocation === 'CENTRAL_COST_CENTER' && (
              <label>
                <span>Central cost center for shuttle costs</span>
                <select
                  value={form.shuttleCentralCostCenterId ?? ''}
                  required
                  onChange={(e) =>
                    setForm({ ...form, shuttleCentralCostCenterId: e.target.value ? Number(e.target.value) : null })
                  }
                >
                  <option value="">—</option>
                  {form.id === session?.plantId &&
                    (costCenters.data ?? []).map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.code} — {c.name}
                      </option>
                    ))}
                </select>
                {form.id !== session?.plantId && <small className="muted">Switch to this plant to pick its cost center.</small>}
              </label>
            )}
            {saved && <div className="ok">Saved.</div>}
            <div className="actions">
              <button type="submit">{form.id ? 'Save' : 'Create'}</button>
              {form.id && (
                <button type="button" className="secondary" onClick={() => setForm(NEW_PLANT)}>
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
