import { createContext, ReactNode, useContext, useState } from 'react';
import { api, setToken } from './api';
import { Session } from './types';

const SESSION_KEY = 'plantride.admin.session';

interface AuthContextValue {
  session: Session | null;
  login: (loginId: string, password: string) => Promise<void>;
  switchPlant: (plantId: number) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function load(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(load);

  const store = (s: Session | null) => {
    setSession(s);
    setToken(s?.token ?? null);
    if (s) localStorage.setItem(SESSION_KEY, JSON.stringify(s));
    else localStorage.removeItem(SESSION_KEY);
  };

  const login = async (loginId: string, password: string) => {
    const s = await api<Session>('/api/auth/login', { method: 'POST', body: { loginId, password } });
    if (s.role !== 'ADMIN' && s.role !== 'DISPATCHER') {
      throw new Error('This console is for admins and the transport control room only.');
    }
    store(s);
  };

  const switchPlant = async (plantId: number) => {
    store(await api<Session>('/api/auth/switch-plant', { method: 'POST', body: { plantId } }));
  };

  return (
    <AuthContext.Provider value={{ session, login, switchPlant, logout: () => store(null) }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
