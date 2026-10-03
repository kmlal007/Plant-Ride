import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { api } from './api';

/**
 * Loads an endpoint while the screen is focused and refreshes it every `intervalMs`.
 * Polling stands in for push notifications until FCM is wired up.
 */
export function usePolling<T>(path: string, intervalMs = 5000) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setData(await api<T>(path));
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [path]);

  useFocusEffect(
    useCallback(() => {
      reload();
      const id = setInterval(reload, intervalMs);
      return () => clearInterval(id);
    }, [reload, intervalMs]),
  );

  return { data, error, reload };
}
