import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import {
  Card,
  Chip,
  Chips,
  Divider,
  EmptyState,
  ErrorText,
  HeaderButton,
  ListRow,
  LiveDot,
  Pill,
  Row,
  Screen,
  SectionTitle,
  T,
} from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { time } from '../../lib/format';
import { greeting } from '../../lib/greeting';
import { LatLng, plantLocation } from '../../lib/location';
import { Arrival, JourneyOption, NearbyStop, Stop } from '../../lib/types';
import { routeColour, useTheme } from '../../theme';

interface StopArrivals {
  stopId: number;
  stopName: string;
  live: Arrival[];
  scheduled: Arrival[];
}

function RouteBadge({ code }: { code: string }) {
  return (
    <View style={{ backgroundColor: routeColour(code), borderRadius: 7, paddingHorizontal: 8, paddingVertical: 3, minWidth: 36 }}>
      <Text style={{ color: '#FFFFFF', fontWeight: '800', textAlign: 'center', fontSize: 13 }}>{code}</Text>
    </View>
  );
}

export default function Shuttles() {
  const { me } = useAuth();
  const t = useTheme();
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
    <Screen
      title="Shuttles & buses"
      subtitle={`${greeting(me?.user.name)} · ${me?.plant.name ?? ''}`}
      right={<HeaderButton icon="locate" label="Refresh location" onPress={locate} />}
    >
      <ErrorText>{error}</ErrorText>

      <Card>
        <SectionTitle icon="location" right={locating ? <T variant="small">Locating…</T> : undefined}>
          {location ? 'Stops near you' : 'Choose your stop'}
        </SectionTitle>
        {approximate && (
          <Pill tone="info" icon="information-circle" label="Outside the plant — showing stops near the plant centre" />
        )}
        {location ? (
          nearby.map((s) => (
            <ListRow
              key={s.stopId}
              icon="bus-outline"
              title={s.name}
              subtitle={`${s.distanceMeters} m away`}
              selected={selected === s.stopId}
              onPress={() => setSelected(s.stopId)}
              right={
                <Row style={{ gap: 4 }}>
                  <Ionicons name="walk" size={16} color={t.muted} />
                  <T variant="small">{s.walkMinutes} min</T>
                </Row>
              }
            />
          ))
        ) : (
          <Chips>
            {allStops.map((s) => (
              <Chip key={s.id} label={s.name} icon="bus-outline" selected={selected === s.id} onPress={() => setSelected(s.id)} />
            ))}
          </Chips>
        )}
      </Card>

      {arrivals && (
        <Card tone="accent">
          <SectionTitle icon="time">Next at {arrivals.stopName}</SectionTitle>
          {arrivals.live.map((a, i) => (
            <Row key={`live-${i}`} style={{ paddingVertical: 4 }}>
              <RouteBadge code={a.routeCode} />
              <View style={{ flex: 1 }}>
                <T variant="strong">{a.routeName}</T>
                <T variant="small">{a.vehicleRegistrationNo}</T>
              </View>
              <View style={{ alignItems: 'flex-end' }}>
                <Text style={{ color: t.ok, fontWeight: '800', fontSize: 17 }}>
                  {a.minutesAway <= 0 ? 'Arriving' : `${a.minutesAway} min`}
                </Text>
                <Row style={{ gap: 5 }}>
                  <LiveDot />
                  <T variant="small">live GPS</T>
                </Row>
              </View>
            </Row>
          ))}
          {arrivals.live.length > 0 && arrivals.scheduled.length > 0 && <Divider />}
          {arrivals.scheduled.map((a, i) => (
            <Row key={`sch-${i}`} style={{ paddingVertical: 3 }}>
              <RouteBadge code={a.routeCode} />
              <T style={{ flex: 1 }} numberOfLines={1}>
                {a.routeName}
              </T>
              <T variant="strong">{time(a.arrivalTime)}</T>
              <T variant="small" style={{ width: 52, textAlign: 'right' }}>
                {a.minutesAway} min
              </T>
            </Row>
          ))}
          {arrivals.live.length === 0 && arrivals.scheduled.length === 0 && (
            <EmptyState icon="moon-outline" title="No more services today" message="The first shuttle runs tomorrow morning." />
          )}
          <T variant="small">Scheduled times come from the timetable; live times from the vehicle's GPS.</T>
        </Card>
      )}

      <Card>
        <SectionTitle icon="flag">Where are you going?</SectionTitle>
        <Chips>
          {allStops.map((s) => (
            <Chip key={s.id} label={s.name} selected={destination === s.id} onPress={() => plan(s.id)} />
          ))}
        </Chips>
        {!location && destination !== null && <T variant="muted">Turn on location to get journey suggestions.</T>}
        {journeys?.length === 0 && (
          <EmptyState icon="car-sport-outline" title="No direct shuttle" message="Book a ride from the Book tab instead." />
        )}
        {journeys?.map((j, i) => (
          <View
            key={i}
            style={{ borderTopWidth: 1, borderTopColor: t.border, paddingTop: 10, gap: 6 }}
          >
            <Row>
              {i === 0 && <Pill tone="ok" icon="flash" label="Fastest" />}
              <T variant="strong" style={{ flex: 1 }}>
                Arrive {time(j.arrivalTime)}
              </T>
              <T variant="muted">{j.totalMinutes} min</T>
            </Row>
            <Row style={{ flexWrap: 'wrap' }}>
              <Ionicons name="walk" size={16} color={t.muted} />
              <T variant="small">{j.boardStop.walkMinutes} min</T>
              <Ionicons name="chevron-forward" size={14} color={t.muted} />
              <RouteBadge code={j.routeCode} />
              <T variant="small">
                {time(j.departureTime)} from {j.boardStop.name}
              </T>
              <Ionicons name="chevron-forward" size={14} color={t.muted} />
              <Ionicons name="flag" size={15} color={t.accent} />
              <T variant="small">{j.alightStopName}</T>
            </Row>
          </View>
        ))}
      </Card>
    </Screen>
  );
}
