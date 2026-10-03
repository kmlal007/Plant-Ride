export type Role = 'ADMIN' | 'DISPATCHER' | 'EMPLOYEE' | 'DRIVER';

export interface Session {
  token: string;
  userId: number;
  name: string;
  role: Role;
  plantId: number;
}

export interface Vehicle {
  id: number;
  registrationNo: string;
  vehicleType: string;
  capacity: number;
  ownerType: string;
  serviceMode: string;
  status: string;
  currentDriverId: number | null;
  lastLat: number | null;
  lastLng: number | null;
  lastSpeedKmh: number | null;
  lastFixAt: string | null;
  active: boolean;
}

export interface Ride {
  id: number;
  status: string;
  rideType: string;
  pickupLabel: string;
  pickupLat: number;
  pickupLng: number;
  dropLabel: string;
  scheduledAt: string | null;
  passengerCount: number;
  purpose: string | null;
  costCenterId: number;
  visitorName: string | null;
  gatePassRef: string | null;
  requesterName: string | null;
  vehicleRegistrationNo: string | null;
  driverName: string | null;
  createdAt: string;
  distanceKm: number | null;
  durationMinutes: number | null;
  distanceSource: string | null;
  fare: number | null;
  cancelReason: string | null;
}
