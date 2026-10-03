export interface Place {
  key: string;
  label: string;
  lat: number;
  lng: number;
}

export interface Department {
  id: number;
  code: string;
  name: string;
  lat: number | null;
  lng: number | null;
}

export interface CostCenter {
  id: number;
  code: string;
  name: string;
}

export interface Project {
  id: number;
  code: string;
  name: string;
}

export interface Stop {
  id: number;
  code: string;
  name: string;
  lat: number;
  lng: number;
}

export interface NearbyStop {
  stopId: number;
  code: string;
  name: string;
  distanceMeters: number;
  walkMinutes: number;
}

export interface Arrival {
  routeId: number;
  routeCode: string;
  routeName: string;
  arrivalTime: string;
  minutesAway: number;
  source: 'LIVE' | 'SCHEDULED';
  vehicleRegistrationNo: string | null;
}

export interface JourneyOption {
  routeCode: string;
  routeName: string;
  boardStop: NearbyStop;
  alightStopName: string;
  departureTime: string;
  arrivalTime: string;
  totalMinutes: number;
}

export interface Ride {
  id: number;
  status: string;
  rideType: 'EXCLUSIVE' | 'SHARED';
  pickupLabel: string;
  dropLabel: string;
  scheduledAt: string | null;
  passengerCount: number;
  purpose: string | null;
  visitorName: string | null;
  requesterName: string | null;
  vehicleRegistrationNo: string | null;
  driverName: string | null;
  driverPhone: string | null;
  otp: string | null;
  createdAt: string;
  distanceKm: number | null;
  durationMinutes: number | null;
  fare: number | null;
  cancelReason: string | null;
}

export interface Me {
  user: { id: number; name: string; loginId: string; role: string; defaultCostCenterId: number | null };
  plant: {
    id: number;
    name: string;
    centerLat: number | null;
    centerLng: number | null;
    visitorModuleEnabled: boolean;
    exclusiveRideRequiresApproval: boolean;
  };
}
