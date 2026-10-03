import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { api, loadToken, saveToken, setUnauthorizedHandler } from './api';

interface Driver {
  id: number;
  name: string;
}

interface AuthState {
  ready: boolean;
  driver: Driver | null;
  login: (loginId: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [driver, setDriver] = useState<Driver | null>(null);

  const loadMe = async () => {
    const me = await api<{ user: Driver & { role: string } }>('/api/me');
    if (me.user.role !== 'DRIVER') throw new Error('This app is for drivers only.');
    setDriver({ id: me.user.id, name: me.user.name });
  };

  useEffect(() => {
    setUnauthorizedHandler(() => setDriver(null));
    (async () => {
      try {
        if (await loadToken()) await loadMe();
      } catch {
        await saveToken(null);
      } finally {
        setReady(true);
      }
    })();
  }, []);

  const login = async (loginId: string, password: string) => {
    const res = await api<{ token: string; role: string }>('/api/auth/login', {
      method: 'POST',
      body: { loginId, password },
    });
    if (res.role !== 'DRIVER') throw new Error('This app is for drivers only.');
    await saveToken(res.token);
    await loadMe();
  };

  const logout = async () => {
    await saveToken(null);
    setDriver(null);
  };

  return <AuthContext.Provider value={{ ready, driver, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
