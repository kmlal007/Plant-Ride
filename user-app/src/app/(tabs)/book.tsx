import { router } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Text, View } from 'react-native';
import { Button, Card, Chip, Chips, colors, ErrorText, Field, Label, Muted, Screen, styles, Title } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { currentLocation } from '../../lib/location';
import { CostCenter, Department, Place, Project, Ride, Stop } from '../../lib/types';

interface Lookups {
  departments: Department[];
  costCenters: CostCenter[];
  projects: Project[];
}

const WHEN = [
  { label: 'Now', minutes: 0 },
  { label: 'In 30 min', minutes: 30 },
  { label: 'In 1 hour', minutes: 60 },
  { label: 'In 2 hours', minutes: 120 },
];

const VEHICLES = [
  { label: 'Any', value: null },
  { label: 'Car', value: 'CAR' },
  { label: 'SUV', value: 'SUV' },
];

export default function Book() {
  const { me } = useAuth();
  const [lookups, setLookups] = useState<Lookups | null>(null);
  const [stops, setStops] = useState<Stop[]>([]);
  const [here, setHere] = useState<Place | null>(null);
  const [pickup, setPickup] = useState<Place | null>(null);
  const [drop, setDrop] = useState<Place | null>(null);
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
      const loc = await currentLocation();
      if (loc) {
        const place = { key: 'here', label: 'My current location', lat: loc.lat, lng: loc.lng };
        setHere(place);
        setPickup((p) => p ?? place);
      }
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (me?.user.defaultCostCenterId && !charge) setCharge({ kind: 'cc', id: me.user.defaultCostCenterId });
  }, [me, charge]);

  const places: Place[] = useMemo(() => {
    const depts = (lookups?.departments ?? [])
      .filter((d) => d.lat !== null && d.lng !== null)
      .map((d) => ({ key: `d${d.id}`, label: d.name, lat: d.lat!, lng: d.lng! }));
    const stopPlaces = stops.map((s) => ({ key: `s${s.id}`, label: `${s.name} (stop)`, lat: s.lat, lng: s.lng }));
    return [...depts, ...stopPlaces];
  }, [lookups, stops]);

  const needsApproval = rideType === 'EXCLUSIVE' && me?.plant.exclusiveRideRequiresApproval;

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
      Alert.alert('Ride requested', `Status: ${ride.status.replace(/_/g, ' ').toLowerCase()}. Your OTP is ${ride.otp}.`);
      setPurpose('');
      setForVisitor(false);
      router.navigate('/rides');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const placeChips = (value: Place | null, set: (p: Place) => void, includeHere: boolean) => (
    <Chips>
      {includeHere && here && <Chip label={here.label} selected={value?.key === 'here'} onPress={() => set(here)} />}
      {places.map((p) => (
        <Chip key={p.key} label={p.label} selected={value?.key === p.key} onPress={() => set(p)} />
      ))}
    </Chips>
  );

  return (
    <Screen>
      <Title>Book a ride</Title>
      <Muted>Rides are free for you and charged to your department or project.</Muted>

      <Card>
        <Label>Ride type</Label>
        <Chips>
          <Chip label="Shared seat" selected={rideType === 'SHARED'} onPress={() => setRideType('SHARED')} />
          <Chip label="Exclusive vehicle" selected={rideType === 'EXCLUSIVE'} onPress={() => setRideType('EXCLUSIVE')} />
        </Chips>
        {needsApproval && <Muted>Exclusive rides need your manager’s approval.</Muted>}
        <Label>Vehicle</Label>
        <Chips>
          {VEHICLES.map((v) => (
            <Chip key={v.label} label={v.label} selected={vehicleType === v.value} onPress={() => setVehicleType(v.value)} />
          ))}
        </Chips>
      </Card>

      <Card>
        <Label>Pickup</Label>
        {placeChips(pickup, setPickup, true)}
        <Label>Drop</Label>
        {placeChips(drop, setDrop, false)}
      </Card>

      <Card>
        <Label>When</Label>
        <Chips>
          {WHEN.map((w) => (
            <Chip key={w.label} label={w.label} selected={whenMinutes === w.minutes} onPress={() => setWhenMinutes(w.minutes)} />
          ))}
        </Chips>
        <View style={styles.row}>
          <Label>Passengers</Label>
          <View style={[styles.row, { gap: 16 }]}>
            <Button title="−" variant="secondary" onPress={() => setPassengers(Math.max(1, passengers - 1))} />
            <Text style={{ fontSize: 18, fontWeight: '700', color: colors.text }}>{passengers}</Text>
            <Button title="+" variant="secondary" onPress={() => setPassengers(Math.min(20, passengers + 1))} />
          </View>
        </View>
        <Field label="Purpose" placeholder="e.g. Safety review meeting" value={purpose} onChangeText={setPurpose} />
      </Card>

      <Card>
        <Label>Charge to</Label>
        <Chips>
          {(lookups?.costCenters ?? []).map((c) => (
            <Chip
              key={`cc${c.id}`}
              label={c.code}
              selected={charge?.kind === 'cc' && charge.id === c.id}
              onPress={() => setCharge({ kind: 'cc', id: c.id })}
            />
          ))}
          {(lookups?.projects ?? []).map((p) => (
            <Chip
              key={`p${p.id}`}
              label={`Project ${p.code}`}
              selected={charge?.kind === 'project' && charge.id === p.id}
              onPress={() => setCharge({ kind: 'project', id: p.id })}
            />
          ))}
        </Chips>
      </Card>

      {me?.plant.visitorModuleEnabled && (
        <Card>
          <Chips>
            <Chip label="This ride is for a visitor / delegate" selected={forVisitor} onPress={() => setForVisitor(!forVisitor)} />
          </Chips>
          {forVisitor && (
            <>
              <Field label="Visitor name" value={visitorName} onChangeText={setVisitorName} />
              <Field label="Visitor mobile" keyboardType="phone-pad" value={visitorPhone} onChangeText={setVisitorPhone} />
              <Field label="Gate pass reference (optional)" value={gatePassRef} onChangeText={setGatePassRef} />
              <Muted>The visitor gets the OTP by SMS.</Muted>
            </>
          )}
        </Card>
      )}

      <ErrorText>{error}</ErrorText>
      <Button title={needsApproval ? 'Request approval' : 'Book ride'} onPress={submit} busy={busy} disabled={!pickup || !drop} />
    </Screen>
  );
}
