import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { api, loadToken, saveToken, setUnauthorizedHandler } from './api';
import { Me } from './types';

interface AuthState {
  ready: boolean;
  me: Me | null;
  login: (loginId: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    setUnauthorizedHandler(() => setMe(null));
    (async () => {
      try {
        if (await loadToken()) setMe(await api<Me>('/api/me'));
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
    if (res.role !== 'EMPLOYEE' && res.role !== 'ADMIN' && res.role !== 'DISPATCHER') {
      throw new Error('Drivers should use the Plant-Ride Driver app.');
    }
    await saveToken(res.token);
    setMe(await api<Me>('/api/me'));
  };

  const logout = async () => {
    await saveToken(null);
    setMe(null);
  };

  return <AuthContext.Provider value={{ ready, me, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
