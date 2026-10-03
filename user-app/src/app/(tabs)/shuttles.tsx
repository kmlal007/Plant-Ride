import { useCallback, useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { Button, Card, Chip, Chips, colors, ErrorText, Label, Muted, Screen, styles, Title } from '../../components/ui';
import { api } from '../../lib/api';
import { time } from '../../lib/format';
import { useAuth } from '../../lib/auth';
import { LatLng, plantLocation } from '../../lib/location';
import { Arrival, JourneyOption, NearbyStop, Stop } from '../../lib/types';

interface StopArrivals {
  stopId: number;
  stopName: string;
  live: Arrival[];
  scheduled: Arrival[];
}

export default function Shuttles() {
  const { me } = useAuth();
  const [location, setLocation] = useState<LatLng | null>(null);
  const [approximate, setApproximate] = useState(false);
  const [locating, setLocating] = useState(true);
  const [nearby, setNearby] = useState<NearbyStop[]>([]);
  const [allStops, setAllStops] = useState<Stop[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [arrivals, setArrivals] = useState<StopArrivals | null>(null);
  const [destination, setDestination] = useState<number | null>(null);
  const [journeys, setJourneys] = useState<JourneyOption[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const locate = useCallback(async () => {
    setLocating(true);
    setError(null);
    try {
      const centre =
        me?.plant.centerLat != null && me?.plant.centerLng != null
          ? { lat: me.plant.centerLat, lng: me.plant.centerLng }
          : null;
      const [where, stops] = await Promise.all([plantLocation(centre), api<Stop[]>('/api/network/stops')]);
      setAllStops(stops);
      const loc = where?.point ?? null;
      setLocation(loc);
      setApproximate(where?.approximate ?? false);
      if (loc) {
        const near = await api<NearbyStop[]>(`/api/network/stops/nearby?lat=${loc.lat}&lng=${loc.lng}&limit=4`);
        setNearby(near);
        if (near.length) setSelected((s) => s ?? near[0].stopId);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLocating(false);
    }
  }, [me]);

  useEffect(() => {
    locate();
  }, [locate]);

  useEffect(() => {
    if (selected === null) return;
    let cancelled = false;
    const load = () =>
      api<StopArrivals>(`/api/network/stops/${selected}/arrivals?limit=4`)
        .then((a) => !cancelled && setArrivals(a))
        .catch((e) => !cancelled && setError(e.message));
    load();
    const id = setInterval(load, 15_000);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [selected]);

  const plan = async (toStopId: number) => {
    setDestination(toStopId);
    setJourneys(null);
    if (!location) return;
    try {
      setJourneys(
        await api<JourneyOption[]>(
          `/api/network/journeys?fromLat=${location.lat}&fromLng=${location.lng}&toStopId=${toStopId}`,
        ),
      );
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <Screen>
      <Title>Shuttles &amp; buses</Title>
      <ErrorText>{error}</ErrorText>

      <Card>
        <View style={styles.row}>
          <Label>{location ? 'Stops near you' : 'Choose a stop'}</Label>
          <Pressable onPress={locate}>
            <Text style={{ color: colors.accent }}>{locating ? 'Locating…' : 'Refresh'}</Text>
          </Pressable>
        </View>
        {approximate && <Muted>You seem to be outside the plant; showing stops near the plant centre.</Muted>}
        {!locating && !location && (
          <Muted>Location is off or unavailable. Pick your stop from the list.</Muted>
        )}
        {location
          ? nearby.map((s) => (
              <Pressable key={s.stopId} onPress={() => setSelected(s.stopId)}>
                <View style={[styles.row, { paddingVertical: 6 }]}>
                  <Text style={{ fontWeight: selected === s.stopId ? '700' : '400', color: colors.text }}>
                    {selected === s.stopId ? '● ' : '○ '}
                    {s.name}
                  </Text>
                  <Muted>
                    {s.distanceMeters} m · {s.walkMinutes} min walk
                  </Muted>
                </View>
              </Pressable>
            ))
          : (
              <Chips>
                {allStops.map((s) => (
                  <Chip key={s.id} label={s.name} selected={selected === s.id} onPress={() => setSelected(s.id)} />
                ))}
              </Chips>
            )}
      </Card>

      {arrivals && (
        <Card>
          <Label>Next at {arrivals.stopName}</Label>
          {arrivals.live.map((a, i) => (
            <View key={`live-${i}`} style={styles.row}>
              <Text style={{ color: colors.text }}>
                {a.routeCode} · {a.vehicleRegistrationNo}
              </Text>
              <Text style={{ color: colors.ok, fontWeight: '700' }}>
                {a.minutesAway <= 0 ? 'Arriving' : `${a.minutesAway} min`} · live
              </Text>
            </View>
          ))}
          {arrivals.scheduled.map((a, i) => (
            <View key={`sch-${i}`} style={styles.row}>
              <Text style={{ color: colors.text }}>
                {a.routeCode} — {a.routeName}
              </Text>
              <Muted>
                {time(a.arrivalTime)} ({a.minutesAway} min)
              </Muted>
            </View>
          ))}
          {arrivals.live.length === 0 && arrivals.scheduled.length === 0 && <Muted>No more services today.</Muted>}
          <Muted style={{ fontSize: 12 }}>Scheduled times are from the timetable; “live” uses vehicle GPS.</Muted>
        </Card>
      )}

      <Card>
        <Label>Where are you going?</Label>
        <Chips>
          {allStops.map((s) => (
            <Chip key={s.id} label={s.name} selected={destination === s.id} onPress={() => plan(s.id)} />
          ))}
        </Chips>
        {!location && destination !== null && <Muted>Turn on location to get journey suggestions.</Muted>}
        {journeys?.length === 0 && <Muted>No direct shuttle found. Try booking a ride instead.</Muted>}
        {journeys?.map((j, i) => (
          <View key={i} style={{ borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 8, gap: 2 }}>
            <Text style={{ fontWeight: '700', color: colors.text }}>
              Arrive {time(j.arrivalTime)} · {j.totalMinutes} min total
            </Text>
            <Muted>
              Walk {j.boardStop.walkMinutes} min to {j.boardStop.name}, board {j.routeCode} at {time(j.departureTime)},
              get off at {j.alightStopName}
            </Muted>
          </View>
        ))}
      </Card>

      {location && nearby.length === 0 && !locating && (
        <Button title="Retry" variant="secondary" onPress={locate} />
      )}
    </Screen>
  );
}
