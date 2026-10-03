import { useState } from 'react';
import { Text, View } from 'react-native';
import { Button, Card, colors, ErrorText, Muted, Screen, Title } from '../../components/ui';
import { api } from '../../lib/api';
import { dateTime } from '../../lib/format';
import { usePolling } from '../../lib/usePolling';
import { Ride } from '../../lib/types';

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
    <Screen>
      <Title>Approvals</Title>
      <ErrorText>{error ?? actionError}</ErrorText>
      {data?.length === 0 && <Muted>Nothing waiting for your approval.</Muted>}
      {(data ?? []).map((r) => (
        <Card key={r.id}>
          <Text style={{ fontWeight: '700', color: colors.text }}>{r.requesterName}</Text>
          <Text style={{ color: colors.text }}>
            {r.pickupLabel} → {r.dropLabel}
          </Text>
          <Muted>
            Exclusive · {r.passengerCount} passenger(s){r.scheduledAt ? ` · ${dateTime(r.scheduledAt)}` : ' · now'}
          </Muted>
          {r.purpose && <Muted>Purpose: {r.purpose}</Muted>}
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <View style={{ flex: 1 }}>
              <Button title="Reject" variant="danger" busy={busyId === r.id} onPress={() => decide(r.id, false)} />
            </View>
            <View style={{ flex: 1 }}>
              <Button title="Approve" busy={busyId === r.id} onPress={() => decide(r.id, true)} />
            </View>
          </View>
        </Card>
      ))}
    </Screen>
  );
}
