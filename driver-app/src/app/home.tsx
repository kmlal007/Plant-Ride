import { Link, Redirect } from 'expo-router';
import { useEffect, useState } from 'react';
import { Linking, Platform, Pressable, Text, View } from 'react-native';
import { Button, Card, colors, ErrorText, Field, Label, Muted, Screen, styles, Title } from '../components/ui';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { DriverStatus, Ride, Vehicle } from '../lib/types';
import { usePolling } from '../lib/usePolling';

const NO_SHOW_WAIT_MS = 5 * 60_000;

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

  return (
    <Screen>
      <View style={styles.row}>
        <Title>{driver?.name ?? 'Driver'}</Title>
        <Pressable onPress={logout} disabled={Boolean(vehicle)}>
          <Text style={{ color: vehicle ? colors.muted : colors.accent }}>Sign out</Text>
        </Pressable>
      </View>
      <ErrorText>{error ?? status.error}</ErrorText>

      {status.data && !vehicle && <StartDuty busy={busy} onStart={(id) => act('/api/driver/duty/start', { vehicleId: id })} />}

      {vehicle && (
        <Card>
          <View style={styles.row}>
            <View>
              <Text style={{ fontWeight: '700', fontSize: 18, color: colors.text }}>{vehicle.registrationNo}</Text>
              <Muted>
                {vehicle.vehicleType} · {vehicle.capacity} seats
              </Muted>
            </View>
            <Text style={{ color: ride ? colors.warn : colors.ok, fontWeight: '700' }}>
              {ride ? 'ON JOB' : 'ONLINE'}
            </Text>
          </View>
        </Card>
      )}

      {vehicle && !ride && (
        <>
          <Card>
            <Text style={{ fontSize: 16, color: colors.text }}>Waiting for ride requests…</Text>
            <Muted>Keep the app open. New requests appear here automatically.</Muted>
          </Card>
          {lastTrip && (
            <Card>
              <Label>Last trip completed</Label>
              <Muted>
                {lastTrip.dropLabel} · {lastTrip.distanceKm} km ({lastTrip.distanceSource === 'GPS' ? 'GPS' : 'estimated'}) ·{' '}
                {lastTrip.durationMinutes} min
              </Muted>
            </Card>
          )}
          <Button title="End duty" variant="secondary" busy={busy} onPress={() => act('/api/driver/duty/end')} />
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

      <Link href="/history" style={{ color: colors.accent, textAlign: 'center', padding: 8 }}>
        Trip history
      </Link>
    </Screen>
  );
}

function StartDuty({ busy, onStart }: { busy: boolean; onStart: (vehicleId: number) => void }) {
  const vehicles = usePolling<Vehicle[]>('/api/driver/vehicles', 15_000);
  return (
    <Card>
      <Label>Start duty — choose your vehicle</Label>
      {vehicles.data?.length === 0 && <Muted>No free vehicles. Contact the transport control room.</Muted>}
      {(vehicles.data ?? []).map((v) => (
        <Button
          key={v.id}
          title={`${v.registrationNo} · ${v.vehicleType}`}
          variant="secondary"
          busy={busy}
          onPress={() => onStart(v.id)}
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
  const now = useNow();
  const [otp, setOtp] = useState('');
  const riderName = ride.visitorName ? `${ride.visitorName} (visitor)` : ride.requesterName;
  const riderPhone = ride.visitorPhone ?? ride.requesterPhone;

  const header = (
    <>
      <Text style={{ color: colors.muted }}>
        {ride.rideType === 'EXCLUSIVE' ? 'Exclusive' : 'Shared'} · {ride.passengerCount} passenger(s)
      </Text>
      <Text style={{ fontSize: 18, fontWeight: '700', color: colors.text }}>Pickup: {ride.pickupLabel}</Text>
      <Text style={{ fontSize: 16, color: colors.text }}>Drop: {ride.dropLabel}</Text>
      {ride.purpose && <Muted>{ride.purpose}</Muted>}
    </>
  );

  if (ride.status === 'OFFERED') {
    const secondsLeft = ride.offerExpiresAt
      ? Math.max(0, Math.round((new Date(ride.offerExpiresAt).getTime() - now) / 1000))
      : 0;
    return (
      <Card style={{ borderColor: colors.warn, borderWidth: 2 }}>
        <Text style={{ fontWeight: '700', color: colors.warn }}>NEW RIDE · respond in {secondsLeft}s</Text>
        {header}
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <View style={{ flex: 1 }}>
            <Button title="Decline" variant="danger" busy={busy} onPress={() => onAction('decline')} />
          </View>
          <View style={{ flex: 2 }}>
            <Button title="Accept" busy={busy} disabled={secondsLeft === 0} onPress={() => onAction('accept')} />
          </View>
        </View>
      </Card>
    );
  }

  const contact = (
    <View style={{ flexDirection: 'row', gap: 8 }}>
      {riderPhone && (
        <View style={{ flex: 1 }}>
          <Button title={`Call ${riderName ?? 'rider'}`} variant="secondary" onPress={() => Linking.openURL(`tel:${riderPhone}`)} />
        </View>
      )}
      <View style={{ flex: 1 }}>
        <Button title="Map" variant="secondary" onPress={() => openMap(ride.pickupLat, ride.pickupLng, ride.pickupLabel)} />
      </View>
    </View>
  );

  if (ride.status === 'ACCEPTED') {
    return (
      <Card>
        <Label>Go to pickup</Label>
        {header}
        {contact}
        <Button title="I have arrived" busy={busy} onPress={() => onAction('arrive')} />
        <Button title="Can't do this ride" variant="secondary" busy={busy} onPress={() => onAction('decline')} />
      </Card>
    );
  }

  if (ride.status === 'DRIVER_ARRIVED') {
    const waitedMs = ride.arrivedAt ? now - new Date(ride.arrivedAt).getTime() : 0;
    const noShowIn = Math.max(0, Math.ceil((NO_SHOW_WAIT_MS - waitedMs) / 60_000));
    return (
      <Card>
        <Label>Waiting for {riderName}</Label>
        {header}
        {contact}
        <Field label="Rider's 4-digit OTP" keyboardType="number-pad" maxLength={4} value={otp} onChangeText={setOtp} />
        <Button title="Start trip" busy={busy} disabled={otp.length !== 4} onPress={() => onAction('start', { otp })} />
        <Button
          title={noShowIn > 0 ? `Rider not here (available in ${noShowIn} min)` : 'Rider not here — mark no-show'}
          variant="danger"
          busy={busy}
          disabled={noShowIn > 0}
          onPress={() => onAction('no-show')}
        />
      </Card>
    );
  }

  return (
    <Card style={{ borderColor: colors.ok, borderWidth: 2 }}>
      <Text style={{ fontWeight: '700', color: colors.ok }}>TRIP IN PROGRESS</Text>
      <Text style={{ fontSize: 18, fontWeight: '700', color: colors.text }}>Drop: {ride.dropLabel}</Text>
      <Muted>Rider: {riderName}</Muted>
      <Button title="Complete trip" busy={busy} onPress={() => onAction('complete')} />
    </Card>
  );
}
