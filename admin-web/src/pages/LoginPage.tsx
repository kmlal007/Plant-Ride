import { BadgeIndianRupee, Bus, CarFront, ShieldCheck } from 'lucide-react';
import { FormEvent, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { PlantIllustration } from '../components/Illustration';
import { PartnerCredit } from '../components/PartnerCredit';

export function LoginPage() {
  const { session, login } = useAuth();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (session) return <Navigate to="/" replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(loginId, password);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <section className="login-brand">
        <div className="brand" style={{ padding: 0 }}>
          <img src="/favicon.svg" alt="" style={{ width: 44, height: 44 }} />
          <div>
            <div className="brand-name" style={{ fontSize: 22 }}>
              Plant<span>Ride</span>
            </div>
            <div className="brand-sub">Plant mobility platform</div>
          </div>
          <span style={{ width: 1, alignSelf: 'stretch', background: 'rgba(255,255,255,0.15)', margin: '0 8px' }} />
          <PartnerCredit />
        </div>
        <div>
          <h1>
            Every shuttle, cab and ride inside the plant — <span>on one screen.</span>
          </h1>
          <p>Live tracking, on-demand dispatch and cost-center charging for employees, visitors and delegates.</p>
          <PlantIllustration />
        </div>
        <div className="login-features">
          <span>
            <Bus /> Timetabled shuttles
          </span>
          <span>
            <CarFront /> On-demand rides
          </span>
          <span>
            <BadgeIndianRupee /> Cost-center billing
          </span>
          <span>
            <ShieldCheck /> Safety alerts
          </span>
        </div>
      </section>
      <section className="login-form-wrap">
        <form className="login-form" onSubmit={submit}>
          <div>
            <h2>Sign in</h2>
            <p className="muted" style={{ margin: '4px 0 0' }}>
              Transport administrators and control room
            </p>
          </div>
          <label>
            Login
            <input value={loginId} autoFocus required autoComplete="username" onChange={(e) => setLoginId(e.target.value)} />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              required
              autoComplete="current-password"
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          {error && <div className="error">{error}</div>}
          <button type="submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </section>
    </div>
  );
}
