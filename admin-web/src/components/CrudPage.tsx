import { FormEvent, useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import { useApi } from '../hooks';

type Row = Record<string, unknown> & { id: number };

export interface Field {
  key: string;
  label: string;
  type?: 'text' | 'number' | 'checkbox' | 'select' | 'password';
  /** Static options, or a lookup endpoint whose rows become options. */
  options?: string[];
  lookup?: { path: string; label: (row: Row) => string; id?: (row: Row) => number };
  required?: boolean;
  /** Hide from the table (e.g. passwords). */
  formOnly?: boolean;
  /** Read-only column: shown in the table, never in the form or payload (e.g. live status). */
  tableOnly?: boolean;
  defaultValue?: unknown;
  help?: string;
}

interface Props {
  title: string;
  description?: string;
  path: string;
  fields: Field[];
  /** Transform form state before sending (e.g. drop empty password on edit). */
  toPayload?: (form: Record<string, unknown>, editing: boolean) => Record<string, unknown>;
}

function emptyForm(fields: Field[]): Record<string, unknown> {
  const f: Record<string, unknown> = {};
  for (const field of fields) {
    f[field.key] = field.defaultValue ?? (field.type === 'checkbox' ? true : '');
  }
  return f;
}

/** Generic list + create/edit form for master data screens. */
export function CrudPage({ title, description, path, fields, toPayload }: Props) {
  const { data, error, reload } = useApi<Row[]>(path);
  const [form, setForm] = useState<Record<string, unknown>>(() => emptyForm(fields));
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [lookups, setLookups] = useState<Record<string, Row[]>>({});

  const lookupPaths = useMemo(
    () => Array.from(new Set(fields.filter((f) => f.lookup).map((f) => f.lookup!.path))),
    [fields],
  );

  useEffect(() => {
    lookupPaths.forEach((p) =>
      api<Row[]>(p)
        .then((rows) => setLookups((prev) => ({ ...prev, [p]: rows })))
        .catch(() => setLookups((prev) => ({ ...prev, [p]: [] }))),
    );
  }, [lookupPaths]);

  const startEdit = (row: Row) => {
    const f = emptyForm(fields);
    for (const field of fields) {
      if (field.type === 'password') continue;
      const v = row[field.key];
      f[field.key] = v === null || v === undefined ? (field.type === 'checkbox' ? false : '') : v;
    }
    setForm(f);
    setEditingId(row.id);
    setFormError(null);
  };

  const reset = () => {
    setForm(emptyForm(fields));
    setEditingId(null);
    setFormError(null);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setFormError(null);
    const payload: Record<string, unknown> = {};
    for (const field of formFields) {
      const v = form[field.key];
      if (field.type === 'number' || (field.lookup && v !== '')) payload[field.key] = v === '' ? null : Number(v);
      else payload[field.key] = v === '' ? null : v;
    }
    try {
      const body = toPayload ? toPayload(payload, editingId !== null) : payload;
      if (editingId === null) await api(path, { method: 'POST', body });
      else await api(`${path}/${editingId}`, { method: 'PUT', body });
      reset();
      reload();
    } catch (err) {
      setFormError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const display = (field: Field, row: Row): string => {
    const v = row[field.key];
    if (v === null || v === undefined || v === '') return '—';
    if (field.type === 'checkbox') return v ? 'Yes' : 'No';
    if (field.lookup) {
      const idOf = field.lookup.id ?? ((r: Row) => r.id);
      const match = lookups[field.lookup.path]?.find((r) => idOf(r) === v);
      return match ? field.lookup.label(match) : String(v);
    }
    return String(v);
  };

  const tableFields = fields.filter((f) => !f.formOnly);
  const formFields = fields.filter((f) => !f.tableOnly);

  return (
    <div className="page">
      <h1>{title}</h1>
      {description && <p className="muted">{description}</p>}
      <div className="split">
        <section className="card grow">
          {error && <div className="error">{error}</div>}
          <table>
            <thead>
              <tr>
                {tableFields.map((f) => (
                  <th key={f.key}>{f.label}</th>
                ))}
                <th />
              </tr>
            </thead>
            <tbody>
              {(data ?? []).map((row) => (
                <tr key={row.id} className={editingId === row.id ? 'selected' : ''}>
                  {tableFields.map((f) => (
                    <td key={f.key}>{display(f, row)}</td>
                  ))}
                  <td>
                    <button className="link" onClick={() => startEdit(row)}>
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
              {data && data.length === 0 && (
                <tr>
                  <td colSpan={tableFields.length + 1} className="muted">
                    Nothing here yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </section>
        <section className="card form-card">
          <h2>{editingId === null ? `New ${title.replace(/s$/, '').toLowerCase()}` : `Edit #${editingId}`}</h2>
          <form onSubmit={submit}>
            {formFields.map((f) => (
              <label key={f.key} className={f.type === 'checkbox' ? 'checkbox' : ''}>
                {f.type === 'checkbox' ? (
                  <>
                    <input
                      type="checkbox"
                      checked={Boolean(form[f.key])}
                      onChange={(e) => setForm({ ...form, [f.key]: e.target.checked })}
                    />
                    {f.label}
                  </>
                ) : (
                  <>
                    <span>
                      {f.label}
                      {f.required && ' *'}
                    </span>
                    {f.options || f.lookup ? (
                      <select
                        value={String(form[f.key] ?? '')}
                        required={f.required}
                        onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
                      >
                        <option value="">—</option>
                        {f.options?.map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                        {f.lookup &&
                          (lookups[f.lookup.path] ?? []).map((r) => {
                            const id = f.lookup!.id ? f.lookup!.id(r) : r.id;
                            return (
                            <option key={id} value={id}>
                              {f.lookup!.label(r)}
                            </option>
                            );
                          })}
                      </select>
                    ) : (
                      <input
                        type={f.type === 'number' ? 'number' : f.type === 'password' ? 'password' : 'text'}
                        step={f.type === 'number' ? 'any' : undefined}
                        value={String(form[f.key] ?? '')}
                        required={f.required && !(f.type === 'password' && editingId !== null)}
                        onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
                      />
                    )}
                  </>
                )}
                {f.help && <small className="muted">{f.help}</small>}
              </label>
            ))}
            {formError && <div className="error">{formError}</div>}
            <div className="actions">
              <button type="submit" disabled={saving}>
                {saving ? 'Saving…' : editingId === null ? 'Create' : 'Save'}
              </button>
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
