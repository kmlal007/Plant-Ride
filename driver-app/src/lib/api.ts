import { Platform } from 'react-native';
import { getItem, setItem } from './storage';

/** In the browser build the API is served from the same origin (nginx proxies /api). */
export const API_URL =
  process.env.EXPO_PUBLIC_API_URL ?? (Platform.OS === 'web' ? '' : 'http://10.0.2.2:8080');
const TOKEN_KEY = 'plantride.driver.token';

let token: string | null = null;
let onUnauthorized: (() => void) | null = null;

export async function loadToken(): Promise<string | null> {
  token = await getItem(TOKEN_KEY);
  return token;
}

export async function saveToken(value: string | null) {
  token = value;
  await setItem(TOKEN_KEY, value);
}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

export async function api<T = unknown>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const sentToken = token;
  if (sentToken) headers.Authorization = `Bearer ${sentToken}`;
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, 'No connection to the server. Check your network and try again.');
  }
  // Only a rejected token means "logged out"; ignore 401s of requests sent before login completed.
  if (res.status === 401 && sentToken && sentToken === token) {
    await saveToken(null);
    onUnauthorized?.();
  }
  const text = await res.text();
  let data: { error?: string } | null = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    // Proxies return HTML error pages (502/504) while the backend restarts.
    if (!res.ok) throw new ApiError(res.status, `Server is unavailable (${res.status}). Please try again shortly.`);
    throw new ApiError(res.status, 'Unexpected response from server');
  }
  if (!res.ok) throw new ApiError(res.status, data?.error ?? `Request failed (${res.status})`);
  return data as T;
}
