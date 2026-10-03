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

import type { IconName, Tone } from '../components/ui';

export interface StatusInfo {
  label: string;
  tone: Tone;
  icon: IconName;
  /** Position on the ride stepper (Requested, Driver assigned, Arrived, On trip, Done). */
  step: number;
}

const STATUS_INFO: Record<string, StatusInfo> = {
  PENDING_APPROVAL: { label: 'Waiting for approval', tone: 'violet', icon: 'hourglass-outline', step: 0 },
  SCHEDULED: { label: 'Scheduled', tone: 'info', icon: 'calendar-outline', step: 0 },
  SEARCHING: { label: 'Finding a vehicle', tone: 'warn', icon: 'search-outline', step: 0 },
  OFFERED: { label: 'Waiting for driver to accept', tone: 'warn', icon: 'time-outline', step: 0 },
  ACCEPTED: { label: 'Driver on the way', tone: 'info', icon: 'navigate-outline', step: 1 },
  DRIVER_ARRIVED: { label: 'Driver has arrived', tone: 'accent', icon: 'location-outline', step: 2 },
  IN_PROGRESS: { label: 'On the way', tone: 'accent', icon: 'car-sport-outline', step: 3 },
  COMPLETED: { label: 'Completed', tone: 'ok', icon: 'checkmark-circle-outline', step: 4 },
  CANCELLED: { label: 'Cancelled', tone: 'neutral', icon: 'close-circle-outline', step: 0 },
  REJECTED: { label: 'Rejected', tone: 'danger', icon: 'ban-outline', step: 0 },
  NO_SHOW: { label: 'Marked no-show', tone: 'danger', icon: 'person-remove-outline', step: 2 },
  UNFULFILLED: { label: 'No vehicle found', tone: 'danger', icon: 'alert-circle-outline', step: 0 },
};

export const RIDE_STEPS = ['Requested', 'Driver assigned', 'Arrived', 'On trip', 'Done'];

export function statusInfo(status: string): StatusInfo {
  return STATUS_INFO[status] ?? { label: status, tone: 'neutral', icon: 'ellipse-outline', step: 0 };
}

const STATUS_TEXT: Record<string, string> = {
  PENDING_APPROVAL: 'Waiting for approval',
  SCHEDULED: 'Scheduled',
  SEARCHING: 'Finding a vehicle',
  OFFERED: 'Waiting for driver to accept',
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
