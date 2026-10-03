import { useState } from 'react';
import { api } from '../api';
import { formatMoney, localDate } from '../format';
import { useApi } from '../hooks';

interface Preview {
  period: string;
  policy: 'NOT_ALLOCATED' | 'CENTRAL_COST_CENTER' | 'HEADCOUNT';
  totalRouteCost: number;
  posted: boolean;
  lines: { costCenterId: number; code: string; name: string; headcount: number; amount: number }[];
}

const POLICY_TEXT: Record<Preview['policy'], string> = {
  NOT_ALLOCATED: 'kept as central overhead (not charged to departments)',
  CENTRAL_COST_CENTER: 'charged to one central cost center',
  HEADCOUNT: 'split across cost centers by employee headcount',
};

/** Monthly allocation of fixed shuttle/bus costs: preview, then post once. */
export function ShuttleCostsPanel({ onPosted }: { onPosted: () => void }) {
  const [month, setMonth] = useState(localDate().slice(0, 7));
  const preview = useApi<Preview>(`/api/admin/shuttle-costs?month=${month}`);
  const [error, setError] = useState<string | null>(null);
  const [posting, setPosting] = useState(false);

  const post = async () => {
    if (!window.confirm(`Post shuttle costs for ${month}? Posted allocations cannot be changed.`)) return;
    setPosting(true);
    setError(null);
    try {
      await api(`/api/admin/shuttle-costs/post?month=${month}`, { method: 'POST' });
      preview.reload();
      onPosted();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setPosting(false);
    }
  };

  const p = preview.data;
  return (
    <section className="card">
      <h2>Shuttle &amp; bus cost allocation</h2>
      <div className="filters">
        <label>
          Month <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
        </label>
        {p && !p.posted && p.lines.length > 0 && (
          <button onClick={post} disabled={posting}>
            {posting ? 'Posting…' : 'Post allocation'}
          </button>
        )}
        {p?.posted && <span className="badge status-COMPLETED">Posted</span>}
      </div>
      {(error || preview.error) && <div className="error">{error ?? preview.error}</div>}
      {p && (
        <>
          <p className="muted">
            Route running cost {formatMoney(p.totalRouteCost)} per month, {POLICY_TEXT[p.policy]}. Change the policy
            under Plants &amp; settings.
          </p>
          {p.lines.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Cost center</th>
                  {p.policy === 'HEADCOUNT' && <th className="num">Headcount</th>}
                  <th className="num">Share</th>
                </tr>
              </thead>
              <tbody>
                {p.lines.map((l) => (
                  <tr key={l.costCenterId}>
                    <td>
                      {l.code} — {l.name}
                    </td>
                    {p.policy === 'HEADCOUNT' && <td className="num">{l.headcount}</td>}
                    <td className="num">{formatMoney(Number(l.amount))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </section>
  );
}
