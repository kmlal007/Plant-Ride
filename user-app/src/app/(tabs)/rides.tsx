import { useState } from 'react';
import { Alert, Linking, Pressable, Text, View } from 'react-native';
import { Button, Card, colors, ErrorText, Muted, Screen, styles, Title } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { ACTIVE_STATUSES, dateTime, money, statusText } from '../../lib/format';
import { usePolling } from '../../lib/usePolling';
import { Ride } from '../../lib/types';

export default function MyRides() {
  const { me, logout } = useAuth();
  const { data, error, reload } = usePolling<Ride[]>('/api/rides/mine', 5000);
  const [actionError, setActionError] = useState<string | null>(null);

  const active = (data ?? []).filter((r) => ACTIVE_STATUSES.includes(r.status));
  const past = (data ?? []).filter((r) => !ACTIVE_STATUSES.includes(r.status));

  const cancel = (ride: Ride) =>
    Alert.alert('Cancel ride?', `${ride.pickupLabel} → ${ride.dropLabel}`, [
      { text: 'Keep', style: 'cancel' },
      {
        text: 'Cancel ride',
        style: 'destructive',
        onPress: async () => {
          try {
            await api(`/api/rides/${ride.id}/cancel`, { method: 'POST', body: {} });
            reload();
          } catch (e) {
            setActionError((e as Error).message);
          }
        },
      },
    ]);

  return (
    <Screen>
      <View style={styles.row}>
        <Title>My rides</Title>
        <Pressable onPress={logout}>
          <Text style={{ color: colors.accent }}>Sign out {me?.user.name}</Text>
        </Pressable>
      </View>
      <ErrorText>{error ?? actionError}</ErrorText>

      {active.map((r) => (
        <Card key={r.id} style={{ borderColor: colors.accent, borderWidth: 1 }}>
          <Text style={{ fontWeight: '700', fontSize: 16, color: colors.text }}>{statusText(r.status)}</Text>
          <Text style={{ color: colors.text }}>
            {r.pickupLabel} → {r.dropLabel}
          </Text>
          {r.scheduledAt && <Muted>Pickup at {dateTime(r.scheduledAt)}</Muted>}
          {r.visitorName && <Muted>For visitor: {r.visitorName}</Muted>}
          {r.vehicleRegistrationNo && (
            <Text style={{ color: colors.text }}>
              {r.vehicleRegistrationNo} · {r.driverName}
            </Text>
          )}
          {r.otp && !r.visitorName && r.status !== 'IN_PROGRESS' && (
            <View style={styles.row}>
              <Muted>Share this OTP with the driver</Muted>
              <Text style={styles.big}>{r.otp}</Text>
            </View>
          )}
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {r.driverPhone && (
              <View style={{ flex: 1 }}>
                <Button title="Call driver" variant="secondary" onPress={() => Linking.openURL(`tel:${r.driverPhone}`)} />
              </View>
            )}
            {r.status !== 'IN_PROGRESS' && (
              <View style={{ flex: 1 }}>
                <Button title="Cancel" variant="danger" onPress={() => cancel(r)} />
              </View>
            )}
          </View>
        </Card>
      ))}

      {past.length > 0 && <Muted>History</Muted>}
      {past.map((r) => (
        <Card key={r.id}>
          <View style={styles.row}>
            <Text style={{ fontWeight: '600', color: colors.text }}>{statusText(r.status)}</Text>
            <Muted>{dateTime(r.createdAt)}</Muted>
          </View>
          <Text style={{ color: colors.text }}>
            {r.pickupLabel} → {r.dropLabel}
          </Text>
          {r.status === 'COMPLETED' && (
            <Muted>
              {r.distanceKm} km · {r.durationMinutes} min · charged {money(r.fare)}
            </Muted>
          )}
          {r.cancelReason && <Muted>{r.cancelReason}</Muted>}
        </Card>
      ))}
      {data?.length === 0 && <Muted>No rides yet. Book one from the “Book ride” tab.</Muted>}
    </Screen>
  );
}
