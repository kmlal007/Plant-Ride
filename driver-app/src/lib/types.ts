export interface Vehicle {
  id: number;
  registrationNo: string;
  vehicleType: string;
  capacity: number;
  status: string;
}

export interface Ride {
  id: number;
  status: 'OFFERED' | 'ACCEPTED' | 'DRIVER_ARRIVED' | 'IN_PROGRESS' | string;
  rideType: 'EXCLUSIVE' | 'SHARED';
  pickupLabel: string;
  pickupLat: number;
  pickupLng: number;
  dropLabel: string;
  passengerCount: number;
  purpose: string | null;
  requesterName: string | null;
  requesterPhone: string | null;
  visitorName: string | null;
  visitorPhone: string | null;
  offerExpiresAt: string | null;
  arrivedAt: string | null;
  completedAt: string | null;
  distanceKm: number | null;
  durationMinutes: number | null;
  distanceSource: string | null;
}

export interface DriverStatus {
  vehicle: Vehicle | null;
  ride: Ride | null;
}
