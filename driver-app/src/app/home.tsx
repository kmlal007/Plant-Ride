import { Redirect, router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Linking, Platform, Text, TextInput, View } from 'react-native';
import { CountdownRing, NumberPlate, RadarIllustration, StageHeader } from '../components/DriverBits';
import {
  Avatar,
  Button,
  Card,
  EmptyState,
  ErrorText,
  HeaderButton,
  ListRow,
  Pill,
  Row,
  RouteSummary,
  Screen,
  SectionTitle,
  T,
} from '../components/ui';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { confirm } from '../lib/dialog';
import { DriverStatus, Ride, Vehicle } from '../lib/types';
import { usePolling } from '../lib/usePolling';
import { radius, useTheme } from '../theme';

const NO_SHOW_WAIT_MS = 5 * 60_000;
const OFFER_SECONDS = 45;

function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

function openMap(lat: number, lng: number, label: string) {
  const url =
    Platform.OS === 'ios'
      ? `http://maps.apple.com/?ll=${lat},${lng}&q=${encodeURIComponent(label)}`
      : `geo:${lat},${lng}?q=${lat},${lng}(${encodeURIComponent(label)})`;
  Linking.openURL(url).catch(() => undefined);
}

export default function Home() {
  const { driver, ready, logout } = useAuth();
  const status = usePolling<DriverStatus>('/api/driver/status', 4000);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastTrip, setLastTrip] = useState<Ride | null>(null);

  if (ready && !driver) return <Redirect href="/login" />;

  const act = async (path: string, body: unknown = {}) => {
    setBusy(true);
    setError(null);
    try {
      const result = await api<Ride | null>(path, { method: 'POST', body });
      await status.reload();
      return result;
    } catch (e) {
      setError((e as Error).message);
      await status.reload();
      return null;
    } finally {
      setBusy(false);
    }
  };

  const vehicle = status.data?.vehicle ?? null;
  const ride = status.data?.ride ?? null;

  const signOut = async () => {
    if (await confirm('Sign out?', driver?.name ?? '', 'Sign out')) logout();
  };

  return (
    <Screen
      variant="driver"
      title={driver?.name ?? 'Driver'}
      subtitle={vehicle ? (ride ? 'On a job' : 'Online · waiting for rides') : 'Off duty'}
      right={
        vehicle ? (
          <HeaderButton icon="time-outline" label="Trip history" onPress={() => router.push('/history')} />
        ) : (
          <HeaderButton icon="log-out-outline" label="Sign out" onPress={signOut} />
        )
      }
      footer={
        vehicle && !ride ? (
          <Button title="End duty" icon="power" variant="secondary" busy={busy} onPress={() => act('/api/driver/duty/end')} />
        ) : undefined
      }
    >
      <ErrorText>{error ?? status.error}</ErrorText>

      {status.data && !vehicle && <StartDuty busy={busy} onStart={(id) => act('/api/driver/duty/start', { vehicleId: id })} />}

      {vehicle && (
        <Card tone={ride ? 'warn' : 'ok'}>
          <Row style={{ justifyContent: 'space-between' }}>
            <View style={{ gap: 6 }}>
              <NumberPlate value={vehicle.registrationNo} />
              <T variant="small">
                {vehicle.vehicleType} · {vehicle.capacity} seats
              </T>
            </View>
            <Pill
              label={ride ? 'ON JOB' : 'ONLINE'}
              tone={ride ? 'warn' : 'ok'}
              icon={ride ? 'briefcase' : 'radio-button-on'}
            />
          </Row>
        </Card>
      )}

      {vehicle && !ride && (
        <>
          <Card>
            <RadarIllustration />
            <T variant="title" style={{ textAlign: 'center' }}>
              Waiting for ride requests
            </T>
            <T variant="muted" style={{ textAlign: 'center' }}>
              Keep the app open. New requests appear here automatically.
            </T>
          </Card>
          {lastTrip && (
            <Card tone="ok">
              <SectionTitle icon="checkmark-circle">Last trip completed</SectionTitle>
              <RouteSummary from={lastTrip.pickupLabel} to={lastTrip.dropLabel} />
              <Row style={{ flexWrap: 'wrap' }}>
                <Pill
                  label={`${lastTrip.distanceKm} km ${lastTrip.distanceSource === 'GPS' ? '(GPS)' : '(estimated)'}`}
                  tone="info"
                  icon="speedometer-outline"
                />
                <Pill label={`${lastTrip.durationMinutes} min`} tone="neutral" icon="time-outline" />
              </Row>
            </Card>
          )}
        </>
      )}

      {ride && (
        <RideCard
          ride={ride}
          busy={busy}
          onAction={async (action, body) => {
            const result = await act(`/api/driver/rides/${ride.id}/${action}`, body);
            if (action === 'complete' && result) setLastTrip(result);
          }}
        />
      )}
    </Screen>
  );
}

function StartDuty({ busy, onStart }: { busy: boolean; onStart: (vehicleId: number) => void }) {
  const vehicles = usePolling<Vehicle[]>('/api/driver/vehicles', 15_000);
  return (
    <Card>
      <SectionTitle icon="key">Start duty</SectionTitle>
      <T variant="muted">Choose the vehicle you are driving today.</T>
      {vehicles.data?.length === 0 && (
        <EmptyState icon="car-outline" title="No free vehicles" message="Contact the transport control room." />
      )}
      {(vehicles.data ?? []).map((v) => (
        <ListRow
          key={v.id}
          icon={v.vehicleType === 'SUV' ? 'car-sport-outline' : 'car-outline'}
          tone="accent"
          title={v.registrationNo}
          subtitle={`${v.vehicleType} · ${v.capacity} seats`}
          onPress={() => !busy && onStart(v.id)}
          right={<Pill label="Start" tone="accent" icon="play" />}
        />
      ))}
    </Card>
  );
}

function RideCard({
  ride,
  busy,
  onAction,
}: {
  ride: Ride;
  busy: boolean;
  onAction: (action: string, body?: unknown) => Promise<void>;
}) {
  const t = useTheme();
  const now = useNow();
  const [otp, setOtp] = useState('');
  const riderName = ride.visitorName ? `${ride.visitorName}` : ride.requesterName ?? 'Rider';
  const riderPhone = ride.visitorPhone ?? ride.requesterPhone;

  const details = (
    <>
      <RouteSummary from={ride.pickupLabel} to={ride.dropLabel} />
      <Row style={{ flexWrap: 'wrap' }}>
        <Pill
          label={ride.rideType === 'EXCLUSIVE' ? 'Exclusive' : 'Shared'}
          tone={ride.rideType === 'EXCLUSIVE' ? 'accent' : 'info'}
          icon={ride.rideType === 'EXCLUSIVE' ? 'car-sport-outline' : 'people-outline'}
        />
        <Pill label={`${ride.passengerCount} passenger${ride.passengerCount > 1 ? 's' : ''}`} icon="person-outline" />
        {ride.visitorName && <Pill label="Visitor" tone="violet" icon="id-card-outline" />}
      </Row>
      {ride.purpose && <T variant="muted">“{ride.purpose}”</T>}
    </>
  );

  const contact = (
    <Row style={{ backgroundColor: t.surface2, borderRadius: radius.md, padding: 10 }}>
      <Avatar name={riderName} />
      <View style={{ flex: 1 }}>
        <T variant="strong">{riderName}</T>
        <T variant="small">{ride.visitorName ? 'Visitor' : 'Employee'}</T>
      </View>
      {riderPhone && (
        <Button title="Call" icon="call" variant="secondary" onPress={() => Linking.openURL(`tel:${riderPhone}`)} />
      )}
    </Row>
  );

  if (ride.status === 'OFFERED') {
    const secondsLeft = ride.offerExpiresAt
      ? Math.max(0, Math.round((new Date(ride.offerExpiresAt).getTime() - now) / 1000))
      : 0;
    return (
      <Card tone="accent" style={{ borderWidth: 2, borderColor: t.accent }}>
        <Row>
          <View style={{ flex: 1 }}>
            <Text style={{ color: t.accent, fontWeight: '900', letterSpacing: 1 }}>NEW RIDE REQUEST</Text>
            <T variant="title">Pickup at {ride.pickupLabel}</T>
          </View>
          <CountdownRing seconds={secondsLeft} total={OFFER_SECONDS} />
        </Row>
        {details}
        <Row>
          <View style={{ flex: 1 }}>
            <Button title="Decline" icon="close" variant="danger" busy={busy} onPress={() => onAction('decline')} size="lg" />
          </View>
          <View style={{ flex: 1.6 }}>
            <Button
              title="Accept"
              icon="checkmark-circle"
              variant="success"
              busy={busy}
              disabled={secondsLeft === 0}
              onPress={() => onAction('accept')}
              size="lg"
            />
          </View>
        </Row>
      </Card>
    );
  }

  if (ride.status === 'ACCEPTED') {
    return (
      <Card>
        <StageHeader step={1} total={3} title="Go to pickup" icon="navigate" />
        {details}
        {contact}
        <Button title="Open map" icon="map-outline" variant="secondary" onPress={() => openMap(ride.pickupLat, ride.pickupLng, ride.pickupLabel)} />
        <Button title="I have arrived" icon="location" onPress={() => onAction('arrive')} busy={busy} size="lg" />
        <Button title="Can't do this ride" variant="ghost" onPress={() => onAction('decline')} busy={busy} />
      </Card>
    );
  }

  if (ride.status === 'DRIVER_ARRIVED') {
    const waitedMs = ride.arrivedAt ? now - new Date(ride.arrivedAt).getTime() : 0;
    const noShowIn = Math.max(0, Math.ceil((NO_SHOW_WAIT_MS - waitedMs) / 60_000));
    return (
      <Card>
        <StageHeader step={2} total={3} title={`Waiting for ${riderName}`} icon="hourglass" />
        {contact}
        <View style={{ gap: 6 }}>
          <T variant="strong">Ask the rider for their 4-digit OTP</T>
          <TextInput
            value={otp}
            onChangeText={(v) => setOtp(v.replace(/\D/g, '').slice(0, 4))}
            keyboardType="number-pad"
            maxLength={4}
            placeholder="• • • •"
            placeholderTextColor={t.muted}
            accessibilityLabel="Rider OTP"
            style={{
              fontSize: 34,
              fontWeight: '900',
              letterSpacing: 18,
              textAlign: 'center',
              color: t.text,
              borderWidth: 2,
              borderColor: otp.length === 4 ? t.accent : t.border,
              borderRadius: radius.md,
              paddingVertical: 12,
              backgroundColor: t.surface2,
            }}
          />
        </View>
        <Button title="Start trip" icon="play" variant="success" busy={busy} disabled={otp.length !== 4} onPress={() => onAction('start', { otp })} size="lg" />
        <Button
          title={noShowIn > 0 ? `Rider not here (in ${noShowIn} min)` : 'Rider not here — mark no-show'}
          icon="person-remove-outline"
          variant="danger"
          busy={busy}
          disabled={noShowIn > 0}
          onPress={() => onAction('no-show')}
        />
      </Card>
    );
  }

  return (
    <Card tone="ok" style={{ borderWidth: 2, borderColor: t.ok }}>
      <StageHeader step={3} total={3} title="Trip in progress" icon="car-sport" />
      <View style={{ backgroundColor: t.surface2, borderRadius: radius.md, padding: 14, gap: 4 }}>
        <T variant="small">DROP AT</T>
        <Text style={{ fontSize: 24, fontWeight: '900', color: t.text }}>{ride.dropLabel}</Text>
      </View>
      {contact}
      <Button title="Complete trip" icon="flag" variant="success" busy={busy} onPress={() => onAction('complete')} size="lg" />
    </Card>
  );
}
