import { router } from 'expo-router';
import { Text, View } from 'react-native';
import { Button, Card, colors, ErrorText, Muted, Screen, styles, Title } from '../components/ui';
import { Ride } from '../lib/types';
import { usePolling } from '../lib/usePolling';

export default function History() {
  const { data, error } = usePolling<Ride[]>('/api/driver/rides/completed', 60_000);
  const totalKm = (data ?? []).reduce((sum, r) => sum + Number(r.distanceKm ?? 0), 0);
  return (
    <Screen>
      <Title>Trip history</Title>
      <ErrorText>{error}</ErrorText>
      <Muted>
        Last {data?.length ?? 0} trips · {totalKm.toFixed(1)} km
      </Muted>
      {(data ?? []).map((r) => (
        <Card key={r.id}>
          <View style={styles.row}>
            <Text style={{ fontWeight: '600', color: colors.text }}>
              {r.pickupLabel} → {r.dropLabel}
            </Text>
          </View>
          <Muted>
            {r.completedAt ? new Date(r.completedAt).toLocaleString() : ''} · {r.distanceKm} km · {r.durationMinutes} min
          </Muted>
        </Card>
      ))}
      <Button title="Back" variant="secondary" onPress={() => router.back()} />
    </Screen>
  );
}
