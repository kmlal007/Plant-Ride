import {
  Ban,
  CalendarClock,
  CarFront,
  CircleCheck,
  Clock,
  Hourglass,
  LucideIcon,
  MapPin,
  Navigation,
  Search,
  TriangleAlert,
  UserX,
  XCircle,
} from 'lucide-react';

type Tone = 'ok' | 'warn' | 'danger' | 'info' | 'violet' | 'accent' | '';

const RIDE: Record<string, [string, Tone, LucideIcon]> = {
  PENDING_APPROVAL: ['Awaiting approval', 'violet', Hourglass],
  SCHEDULED: ['Scheduled', 'info', CalendarClock],
  SEARCHING: ['Finding vehicle', 'warn', Search],
  OFFERED: ['Offered to driver', 'warn', Clock],
  ACCEPTED: ['Driver en route', 'info', Navigation],
  DRIVER_ARRIVED: ['Driver at pickup', 'info', MapPin],
  IN_PROGRESS: ['On trip', 'accent', CarFront],
  COMPLETED: ['Completed', 'ok', CircleCheck],
  CANCELLED: ['Cancelled', '', XCircle],
  REJECTED: ['Rejected', 'danger', Ban],
  NO_SHOW: ['No-show', 'danger', UserX],
  UNFULFILLED: ['Unfulfilled', 'danger', TriangleAlert],
};

const VEHICLE: Record<string, [string, Tone]> = {
  AVAILABLE: ['Available', 'ok'],
  ASSIGNED: ['Assigned', 'warn'],
  ON_TRIP: ['On trip', 'accent'],
  OFF_DUTY: ['Off duty', ''],
  BLOCKED: ['Blocked', 'danger'],
};

/** Map colours for vehicle status, kept in step with the badge tones. */
export const VEHICLE_COLOURS: Record<string, string> = {
  AVAILABLE: '#16a34a',
  ASSIGNED: '#d97706',
  ON_TRIP: '#ea580c',
  OFF_DUTY: '#94a3b8',
  BLOCKED: '#dc2626',
  FIXED_ROUTE: '#2563eb',
};

export function RideStatusBadge({ status }: { status: string }) {
  const [label, tone, Icon] = RIDE[status] ?? [status, '' as Tone, Clock];
  return (
    <span className={`badge ${tone}`}>
      <Icon aria-hidden />
      {label}
    </span>
  );
}

export function VehicleStatusBadge({ status }: { status: string }) {
  const [label, tone] = VEHICLE[status] ?? [status, '' as Tone];
  return <span className={`badge ${tone}`}>{label}</span>;
}

export const RIDE_STATUSES = Object.keys(RIDE);
export const rideStatusLabel = (s: string) => RIDE[s]?.[0] ?? s;
