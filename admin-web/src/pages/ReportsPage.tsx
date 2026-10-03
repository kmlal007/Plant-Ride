import { useState } from 'react';
import { formatMoney, localDate } from '../format';
import { useApi } from '../hooks';

interface Row {
  costCenterId: number;
  code: string;
  name: string;
  rides: number;
  amount: number;
  monthlyBudget: number | null;
}

function monthStart() {
  const d = new Date();
  return localDate(new Date(d.getFullYear(), d.getMonth(), 1));
}

export function ReportsPage() {
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(localDate());
  const report = useApi<Row[]>(`/api/admin/reports/cost-centers?from=${from}&to=${to}`);
  const total = (report.data ?? []).reduce((sum, r) => sum + Number(r.amount), 0);

  const exportCsv = () => {
    const lines = [
      'Cost center code,Cost center name,Rides,Amount (INR),Monthly budget (INR)',
      ...(report.data ?? []).map((r) =>
        [r.code, `"${(r.name ?? '').replace(/"/g, '""')}"`, r.rides, r.amount, r.monthlyBudget ?? ''].join(','),
      ),
    ];
    const blob = new Blob([lines.join('\n')], { type: 'text/csv' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `ride-charges-${from}-to-${to}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  return (
    <div className="page">
      <h1>Cost center charges</h1>
      <p className="muted">
        Completed rides charged to each cost center. Export the CSV for Finance until the SAP posting integration is
        in place.
      </p>
      <div className="filters">
        <label>
          From <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label>
          To <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button onClick={exportCsv} disabled={!report.data?.length}>
          Export CSV
        </button>
      </div>
      {report.error && <div className="error">{report.error}</div>}
      <section className="card">
        <table>
          <thead>
            <tr>
              <th>Cost center</th>
              <th className="num">Rides</th>
              <th className="num">Amount</th>
              <th className="num">Monthly budget</th>
              <th className="num">Used</th>
            </tr>
          </thead>
          <tbody>
            {(report.data ?? []).map((r) => {
              const used = r.monthlyBudget ? Math.round((Number(r.amount) / Number(r.monthlyBudget)) * 100) : null;
              return (
                <tr key={r.costCenterId}>
                  <td>
                    {r.code} — {r.name}
                  </td>
                  <td className="num">{r.rides}</td>
                  <td className="num">{formatMoney(Number(r.amount))}</td>
                  <td className="num">{formatMoney(r.monthlyBudget)}</td>
                  <td className={`num ${used !== null && used >= 80 ? 'warn-text' : ''}`}>
                    {used === null ? '—' : `${used}%`}
                  </td>
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr>
              <th>Total</th>
              <th />
              <th className="num">{formatMoney(total)}</th>
              <th />
              <th />
            </tr>
          </tfoot>
        </table>
        <p className="muted">Budget usage is meaningful when the range covers a single month.</p>
      </section>
    </div>
  );
}
