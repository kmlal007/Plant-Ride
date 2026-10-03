import * as Location from 'expo-location';
import { GeoPoint } from './geo';

export type LatLng = GeoPoint;

/** Give up on GPS after this long; an unanswered permission prompt or no fix must not block the screen. */
const LOCATION_TIMEOUT_MS = 8000;

/** Current position, or null if permission is denied or GPS is unavailable (common deep inside sheds). */
export async function currentLocation(): Promise<LatLng | null> {
  const lookup = async (): Promise<LatLng | null> => {
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') return null;
      const last = await Location.getLastKnownPositionAsync({ maxAge: 60_000 });
      const pos = last ?? (await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }));
      return { lat: pos.coords.latitude, lng: pos.coords.longitude };
    } catch {
      return null;
    }
  };
  const timeout = new Promise<null>((resolve) => setTimeout(() => resolve(null), LOCATION_TIMEOUT_MS));
  return Promise.race([lookup(), timeout]);
}

/** Farther than this from the plant centre, the phone is treated as outside the plant. */
const OUTSIDE_PLANT_KM = 20;

export interface PlantLocation {
  point: LatLng;
  /** True when GPS is unavailable or far away and the plant centre is used instead. */
  approximate: boolean;
}

/**
 * Location for plant features. Employees often plan from home or a meeting room without GPS; in those
 * cases we use the plant centre so stop and ride suggestions still make sense.
 */
export async function plantLocation(centre: LatLng | null): Promise<PlantLocation | null> {
  const here = await currentLocation();
  if (!centre) return here ? { point: here, approximate: false } : null;
  if (here && distanceKm(here, centre) <= OUTSIDE_PLANT_KM) return { point: here, approximate: false };
  return { point: centre, approximate: true };
}

function distanceKm(a: LatLng, b: LatLng): number {
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}
