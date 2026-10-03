import { router } from 'expo-router';
import { View } from 'react-native';
import { Card, EmptyState, ErrorText, HeaderButton, IconBadge, Pill, Row, RouteSummary, Screen, T } from '../components/ui';
import { Ride } from '../lib/types';
import { usePolling } from '../lib/usePolling';

export default function History() {
  const { data, error } = usePolling<Ride[]>('/api/driver/rides/completed', 60_000);
  const totalKm = (data ?? []).reduce((sum, r) => sum + Number(r.distanceKm ?? 0), 0);
  const totalMin = (data ?? []).reduce((sum, r) => sum + Number(r.durationMinutes ?? 0), 0);
  return (
    <Screen
      variant="driver"
      title="Trip history"
      subtitle="Your recent completed trips"
      right={<HeaderButton icon="arrow-back" label="Back" onPress={() => router.back()} />}
    >
      <ErrorText>{error}</ErrorText>
      <Row>
        <Stat icon="flag-outline" value={String(data?.length ?? 0)} label="Trips" />
        <Stat icon="speedometer-outline" value={totalKm.toFixed(1)} label="Km (GPS)" />
        <Stat icon="time-outline" value={String(totalMin)} label="Minutes" />
      </Row>
      {data?.length === 0 && <EmptyState icon="car-outline" title="No trips yet" message="Completed trips appear here." />}
      {(data ?? []).map((r) => (
        <Card key={r.id}>
          <RouteSummary from={r.pickupLabel} to={r.dropLabel} />
          <Row style={{ flexWrap: 'wrap' }}>
            <Pill label={r.completedAt ? new Date(r.completedAt).toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : ''} icon="calendar-outline" />
            <Pill label={`${r.distanceKm} km`} tone="info" icon="speedometer-outline" />
            <Pill label={`${r.durationMinutes} min`} icon="time-outline" />
          </Row>
        </Card>
      ))}
    </Screen>
  );
}

function Stat({ icon, value, label }: { icon: React.ComponentProps<typeof IconBadge>['icon']; value: string; label: string }) {
  return (
    <View style={{ flex: 1 }}>
      <Card style={{ alignItems: 'center', padding: 12, gap: 4 }}>
        <IconBadge icon={icon} tone="accent" size={34} />
        <T variant="title">{value}</T>
        <T variant="small">{label}</T>
      </Card>
    </View>
  );
}
