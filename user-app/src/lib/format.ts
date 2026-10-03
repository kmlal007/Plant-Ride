export function time(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function money(n: number | null | undefined): string {
  return n === null || n === undefined ? '' : `₹${Number(n).toFixed(2)}`;
}

const STATUS_TEXT: Record<string, string> = {
  PENDING_APPROVAL: 'Waiting for approval',
  SCHEDULED: 'Scheduled',
  SEARCHING: 'Finding a vehicle',
  OFFERED: 'Finding a vehicle',
  ACCEPTED: 'Driver on the way',
  DRIVER_ARRIVED: 'Driver has arrived',
  IN_PROGRESS: 'On the way',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  REJECTED: 'Rejected',
  NO_SHOW: 'Marked no-show',
  UNFULFILLED: 'No vehicle found — control room alerted',
};

export function statusText(status: string): string {
  return STATUS_TEXT[status] ?? status;
}

export const ACTIVE_STATUSES = ['PENDING_APPROVAL', 'SCHEDULED', 'SEARCHING', 'OFFERED', 'ACCEPTED', 'DRIVER_ARRIVED', 'IN_PROGRESS', 'UNFULFILLED'];
