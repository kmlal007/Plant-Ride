import { useState } from 'react';
import { View } from 'react-native';
import { Avatar, Button, Card, EmptyState, ErrorText, Pill, Row, RouteSummary, Screen, T } from '../../components/ui';
import { api } from '../../lib/api';
import { dateTime } from '../../lib/format';
import { Ride } from '../../lib/types';
import { usePolling } from '../../lib/usePolling';

/** Managers approve exclusive rides of their reportees. */
export default function Approvals() {
  const { data, error, reload } = usePolling<Ride[]>('/api/rides/approvals', 15_000);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const decide = async (id: number, approve: boolean) => {
    setBusyId(id);
    setActionError(null);
    try {
      await api(`/api/rides/${id}/${approve ? 'approve' : 'reject'}`, { method: 'POST', body: {} });
      reload();
    } catch (e) {
      setActionError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <Screen title="Approvals" subtitle={data?.length ? `${data.length} waiting for you` : 'Exclusive ride requests'}>
      <ErrorText>{error ?? actionError}</ErrorText>
      {data?.length === 0 && (
        <EmptyState icon="checkmark-done-circle-outline" title="All caught up" message="Nothing is waiting for your approval." />
      )}
      {(data ?? []).map((r) => (
        <Card key={r.id} tone="violet">
          <Row>
            <Avatar name={r.requesterName ?? '?'} />
            <View style={{ flex: 1 }}>
              <T variant="strong">{r.requesterName}</T>
              <T variant="small">Requested {dateTime(r.createdAt)}</T>
            </View>
            <Pill label={`${r.passengerCount} pax`} tone="info" icon="people-outline" />
          </Row>
          <RouteSummary from={r.pickupLabel} to={r.dropLabel} />
          <Row style={{ flexWrap: 'wrap' }}>
            <Pill label="Exclusive vehicle" tone="accent" icon="car-sport-outline" />
            <Pill label={r.scheduledAt ? dateTime(r.scheduledAt) : 'Now'} tone="neutral" icon="time-outline" />
          </Row>
          {r.purpose && <T variant="muted">“{r.purpose}”</T>}
          <Row>
            <View style={{ flex: 1 }}>
              <Button title="Reject" icon="close" variant="danger" busy={busyId === r.id} onPress={() => decide(r.id, false)} />
            </View>
            <View style={{ flex: 1 }}>
              <Button title="Approve" icon="checkmark" variant="success" busy={busyId === r.id} onPress={() => decide(r.id, true)} />
            </View>
          </Row>
        </Card>
      ))}
    </Screen>
  );
}
