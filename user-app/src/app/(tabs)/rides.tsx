import { useState } from 'react';
import { Linking, View } from 'react-native';
import {
  Avatar,
  Button,
  Card,
  EmptyState,
  ErrorText,
  HeaderButton,
  IconBadge,
  Pill,
  Row,
  RouteSummary,
  Screen,
  SectionTitle,
  Stepper,
  T,
  OtpDigits,
} from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { confirm } from '../../lib/dialog';
import { ACTIVE_STATUSES, dateTime, money, RIDE_STEPS, statusInfo } from '../../lib/format';
import { Ride } from '../../lib/types';
import { usePolling } from '../../lib/usePolling';
import { useTheme } from '../../theme';

export default function MyRides() {
  const { me, logout } = useAuth();
  const t = useTheme();
  const { data, error, reload } = usePolling<Ride[]>('/api/rides/mine', 5000);
  const [actionError, setActionError] = useState<string | null>(null);

  const active = (data ?? []).filter((r) => ACTIVE_STATUSES.includes(r.status));
  const past = (data ?? []).filter((r) => !ACTIVE_STATUSES.includes(r.status));

  const cancel = async (ride: Ride) => {
    if (!(await confirm('Cancel ride?', `${ride.pickupLabel} → ${ride.dropLabel}`, 'Cancel ride'))) return;
    try {
      await api(`/api/rides/${ride.id}/cancel`, { method: 'POST', body: {} });
      reload();
    } catch (e) {
      setActionError((e as Error).message);
    }
  };

  const signOut = async () => {
    if (await confirm('Sign out?', me?.user.name ?? '', 'Sign out')) logout();
  };

  return (
    <Screen
      title="My rides"
      subtitle={me?.user.name}
      right={<HeaderButton icon="log-out-outline" label="Sign out" onPress={signOut} />}
    >
      <ErrorText>{error ?? actionError}</ErrorText>

      {active.map((r) => {
        const info = statusInfo(r.status);
        const showOtp = r.otp && !r.visitorName && ['OFFERED', 'ACCEPTED', 'DRIVER_ARRIVED'].includes(r.status);
        return (
          <Card key={r.id} tone={info.tone}>
            <Row>
              <IconBadge icon={info.icon} tone={info.tone} />
              <View style={{ flex: 1 }}>
                <T variant="title">{info.label}</T>
                {r.scheduledAt ? <T variant="small">Pickup {dateTime(r.scheduledAt)}</T> : <T variant="small">Requested {dateTime(r.createdAt)}</T>}
              </View>
              <Pill label={r.rideType === 'EXCLUSIVE' ? 'Exclusive' : 'Shared'} tone={r.rideType === 'EXCLUSIVE' ? 'accent' : 'info'} />
            </Row>
            {!['PENDING_APPROVAL', 'SCHEDULED', 'UNFULFILLED'].includes(r.status) && (
              <Stepper steps={RIDE_STEPS} current={info.step} />
            )}
            <RouteSummary from={r.pickupLabel} to={r.dropLabel} />
            {r.visitorName && <Pill tone="violet" icon="person-outline" label={`For ${r.visitorName}`} />}
            {r.vehicleRegistrationNo && (
              <Row style={{ backgroundColor: t.surface2, borderRadius: 12, padding: 10 }}>
                <Avatar name={r.driverName ?? 'Driver'} />
                <View style={{ flex: 1 }}>
                  <T variant="strong">{r.driverName}</T>
                  <T variant="small">{r.vehicleRegistrationNo}</T>
                </View>
                {r.driverPhone && (
                  <HeaderButtonLight onPress={() => Linking.openURL(`tel:${r.driverPhone}`)} />
                )}
              </Row>
            )}
            {showOtp && (
              <View style={{ alignItems: 'center', gap: 6, paddingVertical: 4 }}>
                <T variant="small">Share this OTP with the driver at pickup</T>
                <OtpDigits code={r.otp!} />
              </View>
            )}
            {r.status !== 'IN_PROGRESS' && (
              <Button title="Cancel ride" icon="close-circle-outline" variant="danger" onPress={() => cancel(r)} />
            )}
          </Card>
        );
      })}

      {past.length > 0 && <SectionTitle icon="time-outline">History</SectionTitle>}
      {past.map((r) => {
        const info = statusInfo(r.status);
        return (
          <Card key={r.id}>
            <Row>
              <Pill label={info.label} tone={info.tone} icon={info.icon} />
              <T variant="small" style={{ flex: 1, textAlign: 'right' }}>
                {dateTime(r.createdAt)}
              </T>
            </Row>
            <RouteSummary from={r.pickupLabel} to={r.dropLabel} />
            {r.status === 'COMPLETED' && (
              <Row style={{ justifyContent: 'space-between' }}>
                <T variant="muted">
                  {r.distanceKm} km · {r.durationMinutes} min
                </T>
                <T variant="strong">{money(r.fare)}</T>
              </Row>
            )}
            {r.cancelReason && <T variant="small">{r.cancelReason}</T>}
          </Card>
        );
      })}
      {data?.length === 0 && (
        <EmptyState icon="car-sport-outline" title="No rides yet" message="Book a ride from the Book tab, or check shuttle timings." />
      )}
    </Screen>
  );
}

function HeaderButtonLight({ onPress }: { onPress: () => void }) {
  return (
    <View>
      <Button title="Call" icon="call" variant="secondary" onPress={onPress} />
    </View>
  );
}
