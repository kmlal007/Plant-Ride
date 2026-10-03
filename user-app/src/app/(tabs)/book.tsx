import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import {
  Button,
  Card,
  Chip,
  Chips,
  ErrorText,
  Field,
  IconName,
  ListRow,
  Pill,
  Row,
  Screen,
  SectionTitle,
  Segmented,
  T,
} from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { notify } from '../../lib/dialog';
import { plantLocation } from '../../lib/location';
import { CostCenter, Department, Place, Project, Ride, Stop } from '../../lib/types';
import { radius, useTheme } from '../../theme';

interface Lookups {
  departments: Department[];
  costCenters: CostCenter[];
  projects: Project[];
}

type PlaceOption = Place & { icon: IconName; kind: string };

const WHEN = [
  { label: 'Now', minutes: 0 },
  { label: 'In 30 min', minutes: 30 },
  { label: 'In 1 hour', minutes: 60 },
  { label: 'In 2 hours', minutes: 120 },
];

const VEHICLES: { label: string; value: string | null; icon: IconName }[] = [
  { label: 'Any', value: null, icon: 'apps-outline' },
  { label: 'Car', value: 'CAR', icon: 'car-outline' },
  { label: 'SUV', value: 'SUV', icon: 'car-sport-outline' },
];

function PlaceRow({
  label,
  value,
  marker,
  active,
  onPress,
}: {
  label: string;
  value: string | null;
  marker: 'pickup' | 'drop';
  active: boolean;
  onPress: () => void;
}) {
  const t = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${label}: ${value ?? 'not chosen'}`}
      onPress={onPress}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
        padding: 12,
        borderRadius: radius.md,
        borderWidth: 1.5,
        borderColor: active ? t.accent : t.border,
        backgroundColor: active ? t.accentSoft : t.surface2,
      }}
    >
      {marker === 'pickup' ? (
        <View style={{ width: 14, height: 14, borderRadius: 7, borderWidth: 3.5, borderColor: t.ok }} />
      ) : (
        <View style={{ width: 14, height: 14, borderRadius: 3, backgroundColor: t.accent }} />
      )}
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: 12, color: t.muted, fontWeight: '600' }}>{label.toUpperCase()}</Text>
        <Text style={{ fontSize: 16, fontWeight: '700', color: value ? t.text : t.muted }} numberOfLines={1}>
          {value ?? `Choose ${label.toLowerCase()}`}
        </Text>
      </View>
      <Ionicons name={active ? 'chevron-up' : 'chevron-down'} size={18} color={t.muted} />
    </Pressable>
  );
}

export default function Book() {
  const { me } = useAuth();
  const t = useTheme();
  const [lookups, setLookups] = useState<Lookups | null>(null);
  const [stops, setStops] = useState<Stop[]>([]);
  const [here, setHere] = useState<PlaceOption | null>(null);
  const [pickup, setPickup] = useState<PlaceOption | null>(null);
  const [drop, setDrop] = useState<PlaceOption | null>(null);
  const [picking, setPicking] = useState<'pickup' | 'drop' | null>(null);
  const [rideType, setRideType] = useState<'EXCLUSIVE' | 'SHARED'>('SHARED');
  const [vehicleType, setVehicleType] = useState<string | null>(null);
  const [whenMinutes, setWhenMinutes] = useState(0);
  const [passengers, setPassengers] = useState(1);
  const [purpose, setPurpose] = useState('');
  const [charge, setCharge] = useState<{ kind: 'cc' | 'project'; id: number } | null>(null);
  const [forVisitor, setForVisitor] = useState(false);
  const [visitorName, setVisitorName] = useState('');
  const [visitorPhone, setVisitorPhone] = useState('');
  const [gatePassRef, setGatePassRef] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [l, s] = await Promise.all([api<Lookups>('/api/me/lookups'), api<Stop[]>('/api/network/stops')]);
      setLookups(l);
      setStops(s);
      const centre =
        me?.plant.centerLat != null && me?.plant.centerLng != null
          ? { lat: me.plant.centerLat, lng: me.plant.centerLng }
          : null;
      const where = await plantLocation(centre);
      // Outside the plant there is no meaningful "here": riders pick a department or stop instead.
      if (where && !where.approximate) {
        const place: PlaceOption = {
          key: 'here',
          label: 'My current location',
          lat: where.point.lat,
          lng: where.point.lng,
          icon: 'locate',
          kind: 'GPS',
        };
        setHere(place);
        setPickup((p) => p ?? place);
      }
    } catch (e) {
      setError((e as Error).message);
    }
  }, [me]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (me?.user.defaultCostCenterId && !charge) setCharge({ kind: 'cc', id: me.user.defaultCostCenterId });
  }, [me, charge]);

  const places: PlaceOption[] = useMemo(() => {
    const depts = (lookups?.departments ?? [])
      .filter((d) => d.lat !== null && d.lng !== null)
      .map((d) => ({ key: `d${d.id}`, label: d.name, lat: d.lat!, lng: d.lng!, icon: 'business-outline' as IconName, kind: 'Department' }));
    const stopPlaces = stops.map((s) => ({
      key: `s${s.id}`,
      label: s.name,
      lat: s.lat,
      lng: s.lng,
      icon: 'bus-outline' as IconName,
      kind: 'Shuttle stop',
    }));
    return [...(here ? [here] : []), ...depts, ...stopPlaces];
  }, [lookups, stops, here]);

  const needsApproval = rideType === 'EXCLUSIVE' && me?.plant.exclusiveRideRequiresApproval;

  const choose = (p: PlaceOption) => {
    if (picking === 'pickup') setPickup(p);
    else setDrop(p);
    setPicking(picking === 'pickup' && !drop ? 'drop' : null);
  };

  const submit = async () => {
    if (!pickup || !drop) {
      setError('Choose pickup and drop.');
      return;
    }
    if (pickup.key === drop.key) {
      setError('Pickup and drop must be different.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const ride = await api<Ride>('/api/rides', {
        method: 'POST',
        body: {
          rideType,
          vehicleType,
          pickupLabel: pickup.label,
          pickupLat: pickup.lat,
          pickupLng: pickup.lng,
          dropLabel: drop.label,
          dropLat: drop.lat,
          dropLng: drop.lng,
          scheduledAt: whenMinutes ? new Date(Date.now() + whenMinutes * 60_000).toISOString() : null,
          passengerCount: passengers,
          purpose: purpose || null,
          costCenterId: charge?.kind === 'cc' ? charge.id : null,
          projectId: charge?.kind === 'project' ? charge.id : null,
          visitorName: forVisitor ? visitorName : null,
          visitorPhone: forVisitor ? visitorPhone : null,
          gatePassRef: forVisitor ? gatePassRef : null,
        },
      });
      notify('Ride requested', `Status: ${ride.status.replace(/_/g, ' ').toLowerCase()}. Your OTP is ${ride.otp}.`);
      setPurpose('');
      setForVisitor(false);
      router.navigate('/rides');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const chargeLabel =
    charge?.kind === 'project'
      ? lookups?.projects.find((p) => p.id === charge.id)?.code
      : lookups?.costCenters.find((c) => c.id === charge?.id)?.code;

  return (
    <Screen
      title="Book a ride"
      subtitle="Free for you · charged to your department"
      footer={
        <View style={{ gap: 8 }}>
          {pickup && drop && (
            <T variant="small" style={{ textAlign: 'center' }}>
              {rideType === 'SHARED' ? 'Shared seat' : 'Exclusive vehicle'} · {passengers} pax ·{' '}
              {WHEN.find((w) => w.minutes === whenMinutes)?.label} · {chargeLabel ?? 'no cost center'}
            </T>
          )}
          <Button
            title={needsApproval ? 'Request approval' : 'Book ride'}
            icon={needsApproval ? 'paper-plane-outline' : 'checkmark-circle-outline'}
            onPress={submit}
            busy={busy}
            disabled={!pickup || !drop}
            size="lg"
          />
        </View>
      }
    >
      <ErrorText>{error}</ErrorText>

      <Card>
        <SectionTitle icon="navigate">Trip</SectionTitle>
        <PlaceRow
          label="Pickup"
          marker="pickup"
          value={pickup?.label ?? null}
          active={picking === 'pickup'}
          onPress={() => setPicking(picking === 'pickup' ? null : 'pickup')}
        />
        <PlaceRow
          label="Drop"
          marker="drop"
          value={drop?.label ?? null}
          active={picking === 'drop'}
          onPress={() => setPicking(picking === 'drop' ? null : 'drop')}
        />
        {picking && (
          <View style={{ borderTopWidth: 1, borderTopColor: t.border, paddingTop: 6 }}>
            {places.map((p) => (
              <ListRow
                key={p.key}
                icon={p.icon}
                tone={p.key === 'here' ? 'ok' : 'neutral'}
                title={p.label}
                subtitle={p.kind}
                selected={(picking === 'pickup' ? pickup : drop)?.key === p.key}
                onPress={() => choose(p)}
              />
            ))}
          </View>
        )}
      </Card>

      <Card>
        <SectionTitle icon="options">Ride</SectionTitle>
        <Segmented
          value={rideType}
          onChange={setRideType}
          options={[
            { value: 'SHARED', label: 'Shared seat', icon: 'people-outline', hint: 'Lower cost' },
            {
              value: 'EXCLUSIVE',
              label: 'Exclusive',
              icon: 'car-sport-outline',
              hint: me?.plant.exclusiveRideRequiresApproval ? 'Needs manager approval' : 'Whole vehicle',
            },
          ]}
        />
        <Chips>
          {VEHICLES.map((v) => (
            <Chip key={v.label} label={v.label} icon={v.icon} selected={vehicleType === v.value} onPress={() => setVehicleType(v.value)} />
          ))}
        </Chips>
      </Card>

      <Card>
        <SectionTitle icon="time">When &amp; who</SectionTitle>
        <Chips>
          {WHEN.map((w) => (
            <Chip key={w.label} label={w.label} selected={whenMinutes === w.minutes} onPress={() => setWhenMinutes(w.minutes)} />
          ))}
        </Chips>
        <Row style={{ justifyContent: 'space-between', paddingVertical: 4 }}>
          <Row>
            <Ionicons name="people" size={18} color={t.muted} />
            <T variant="strong">Passengers</T>
          </Row>
          <Row style={{ gap: 14 }}>
            <StepButton icon="remove" onPress={() => setPassengers(Math.max(1, passengers - 1))} label="Fewer passengers" />
            <Text style={{ fontSize: 20, fontWeight: '800', color: t.text, minWidth: 22, textAlign: 'center' }}>{passengers}</Text>
            <StepButton icon="add" onPress={() => setPassengers(Math.min(20, passengers + 1))} label="More passengers" />
          </Row>
        </Row>
        <Field
          label="Purpose"
          icon="chatbox-ellipses-outline"
          placeholder="e.g. Safety review meeting"
          value={purpose}
          onChangeText={setPurpose}
        />
      </Card>

      <Card>
        <SectionTitle icon="wallet">Charge to</SectionTitle>
        <Chips>
          {(lookups?.costCenters ?? []).map((c) => (
            <Chip
              key={`cc${c.id}`}
              label={c.code}
              icon="wallet-outline"
              selected={charge?.kind === 'cc' && charge.id === c.id}
              onPress={() => setCharge({ kind: 'cc', id: c.id })}
            />
          ))}
          {(lookups?.projects ?? []).map((p) => (
            <Chip
              key={`p${p.id}`}
              label={p.code}
              icon="briefcase-outline"
              selected={charge?.kind === 'project' && charge.id === p.id}
              onPress={() => setCharge({ kind: 'project', id: p.id })}
            />
          ))}
        </Chips>
      </Card>

      {me?.plant.visitorModuleEnabled && (
        <Card tone={forVisitor ? 'violet' : undefined}>
          <ListRow
            icon="person-add-outline"
            tone="violet"
            title="Book for a visitor or delegate"
            subtitle="They receive the OTP by SMS"
            onPress={() => setForVisitor(!forVisitor)}
            right={<Ionicons name={forVisitor ? 'checkbox' : 'square-outline'} size={24} color={forVisitor ? t.violet : t.muted} />}
          />
          {forVisitor && (
            <>
              <Field label="Visitor name" icon="person-outline" value={visitorName} onChangeText={setVisitorName} />
              <Field
                label="Visitor mobile"
                icon="call-outline"
                keyboardType="phone-pad"
                value={visitorPhone}
                onChangeText={setVisitorPhone}
              />
              <Field label="Gate pass reference (optional)" icon="document-text-outline" value={gatePassRef} onChangeText={setGatePassRef} />
              <Pill tone="violet" icon="shield-checkmark-outline" label="Gate pass stays in the plant's visitor system" />
            </>
          )}
        </Card>
      )}
    </Screen>
  );
}

function StepButton({ icon, onPress, label }: { icon: IconName; onPress: () => void; label: string }) {
  const t = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      style={({ pressed }) => ({
        width: 40,
        height: 40,
        borderRadius: 20,
        borderWidth: 1.5,
        borderColor: t.borderStrong,
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: pressed ? t.surface2 : t.surface,
      })}
    >
      <Ionicons name={icon} size={20} color={t.text} />
    </Pressable>
  );
}
