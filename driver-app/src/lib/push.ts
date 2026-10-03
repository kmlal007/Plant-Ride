import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { api } from './api';
import { getItem, setItem } from './storage';

const TOKEN_KEY = 'plantride.driver.pushToken';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

/**
 * Registers this install's native push token (an FCM token on Android) with the backend. Failures are
 * non-fatal: without Firebase configuration in the build, or in the browser, the app relies on polling.
 */
export async function registerForPush(app: 'user' | 'driver'): Promise<void> {
  if (Platform.OS === 'web' || !Device.isDevice) return;
  try {
    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'Ride updates',
        importance: Notifications.AndroidImportance.HIGH,
      });
    }
    let { status } = await Notifications.getPermissionsAsync();
    if (status !== 'granted') status = (await Notifications.requestPermissionsAsync()).status;
    if (status !== 'granted') return;
    const token = await Notifications.getDevicePushTokenAsync();
    await api('/api/me/devices', {
      method: 'POST',
      body: { token: String(token.data), platform: Platform.OS, app },
    });
    await setItem(TOKEN_KEY, String(token.data));
  } catch (e) {
    console.warn('Push registration skipped:', (e as Error).message);
  }
}

/** Called before logout so the next person on this phone does not receive these notifications. */
export async function unregisterPush(): Promise<void> {
  const token = await getItem(TOKEN_KEY);
  if (!token) return;
  try {
    await api('/api/me/devices/unregister', { method: 'POST', body: { token } });
  } catch {
    // Best effort; the backend also drops tokens that FCM reports as invalid.
  }
  await setItem(TOKEN_KEY, null);
}
