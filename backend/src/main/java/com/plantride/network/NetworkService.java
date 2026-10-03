package com.plantride.network;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.common.ApiException;
import com.plantride.common.GeoUtils;
import com.plantride.fleet.Vehicle;
import com.plantride.fleet.VehicleRepository;
import com.plantride.plant.Plant;
import com.plantride.plant.PlantService;

/** Rider-facing queries over fixed routes: nearest stops, next arrivals, journey suggestions. */
@Service
@Transactional(readOnly = true)
public class NetworkService {

    /** GPS fixes older than this are not trusted for live arrival estimates. */
    static final Duration LIVE_FIX_MAX_AGE = Duration.ofMinutes(3);
    /** How far a rider is assumed willing to walk to a stop. */
    static final double MAX_WALK_KM = 1.5;

    public record NearbyStop(Long stopId, String code, String name, double lat, double lng,
                             int distanceMeters, int walkMinutes) {
    }

    public record Arrival(Long routeId, String routeCode, String routeName, Instant arrivalTime,
                          long minutesAway, String source, String vehicleRegistrationNo) {
    }

    public record StopArrivals(Long stopId, String stopName, List<Arrival> live, List<Arrival> scheduled) {
    }

    public record JourneyOption(Long routeId, String routeCode, String routeName,
                                NearbyStop boardStop, Long alightStopId, String alightStopName,
                                Instant departureTime, Instant arrivalTime, long totalMinutes) {
    }

    private final StopRepository stops;
    private final RouteRepository routes;
    private final RouteStopRepository routeStops;
    private final VehicleRepository vehicles;
    private final PlantService plantService;
    private final Clock clock;

    public NetworkService(StopRepository stops, RouteRepository routes, RouteStopRepository routeStops,
                          VehicleRepository vehicles, PlantService plantService, Clock clock) {
        this.stops = stops;
        this.routes = routes;
        this.routeStops = routeStops;
        this.vehicles = vehicles;
        this.plantService = plantService;
        this.clock = clock;
    }

    public List<NearbyStop> nearbyStops(Long plantId, double lat, double lng, int limit) {
        return stops.findByPlantIdAndActiveTrue(plantId).stream()
                .map(s -> toNearby(s, lat, lng))
                .sorted(Comparator.comparingInt(NearbyStop::distanceMeters))
                .limit(limit)
                .toList();
    }

    public StopArrivals arrivals(Long plantId, Long stopId, int limit) {
        Stop stop = stops.require(stopId, plantId, "Stop");
        ZoneId zone = zone(plantId);
        Instant now = clock.instant();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, zone);

        List<Arrival> scheduled = new ArrayList<>();
        List<Arrival> live = new ArrayList<>();
        for (RouteStop rs : routeStops.findByStopId(stopId)) {
            Route route = routes.findByIdAndPlantId(rs.getRouteId(), plantId).orElse(null);
            if (route == null || !route.isActive()) {
                continue;
            }
            for (LocalDateTime t : ScheduleCalculator.nextArrivals(route, rs.getOffsetMinutes(), localNow, limit)) {
                Instant at = t.atZone(zone).toInstant();
                scheduled.add(new Arrival(route.getId(), route.getCode(), route.getName(), at,
                        minutesBetween(now, at), "SCHEDULED", null));
            }
            live.addAll(liveArrivals(route, rs, now));
        }
        scheduled.sort(Comparator.comparing(Arrival::arrivalTime));
        live.sort(Comparator.comparing(Arrival::arrivalTime));
        return new StopArrivals(stop.getId(), stop.getName(), live,
                scheduled.subList(0, Math.min(limit, scheduled.size())));
    }

    /**
     * Live estimate: place each tracked vehicle of the route at its nearest route stop and add the
     * timetable offset difference to the target stop. Deliberately simple; it ignores traffic and
     * assumes the vehicle runs the stops in sequence.
     */
    List<Arrival> liveArrivals(Route route, RouteStop target, Instant now) {
        List<RouteStop> pattern = routeStops.findByRouteIdOrderBySeqAsc(route.getId());
        Map<Long, Stop> stopById = stopsById(route.getPlantId());
        List<Arrival> result = new ArrayList<>();
        for (Vehicle v : vehicles.findByRouteIdAndActiveTrue(route.getId())) {
            if (v.getLastFixAt() == null || v.getLastFixAt().isBefore(now.minus(LIVE_FIX_MAX_AGE))) {
                continue;
            }
            RouteStop nearest = pattern.stream()
                    .min(Comparator.comparingDouble(rs -> {
                        Stop s = stopById.get(rs.getStopId());
                        return GeoUtils.haversineKm(v.getLastLat(), v.getLastLng(), s.getLat(), s.getLng());
                    }))
                    .orElse(null);
            if (nearest == null || nearest.getSeq() > target.getSeq()) {
                continue;
            }
            int minutes = target.getOffsetMinutes() - nearest.getOffsetMinutes();
            Instant at = now.plus(Duration.ofMinutes(minutes));
            result.add(new Arrival(route.getId(), route.getCode(), route.getName(), at, minutes, "LIVE",
                    v.getRegistrationNo()));
        }
        return result;
    }

    /**
     * Direct (no transfer) journey options from a location to a destination stop, ranked by
     * arrival time: walk to a nearby boarding stop, wait for the next scheduled trip, ride, arrive.
     */
    public List<JourneyOption> journeys(Long plantId, double fromLat, double fromLng, Long toStopId, int limit) {
        Stop destination = stops.require(toStopId, plantId, "Destination stop");
        ZoneId zone = zone(plantId);
        Instant now = clock.instant();
        Map<Long, Stop> stopById = stopsById(plantId);

        List<NearbyStop> boardCandidates = nearbyStops(plantId, fromLat, fromLng, 5).stream()
                .filter(s -> s.distanceMeters() <= MAX_WALK_KM * 1000 || s.stopId().equals(toStopId))
                .toList();
        if (boardCandidates.isEmpty()) {
            return List.of();
        }

        List<Route> activeRoutes = routes.findByPlantIdAndActiveTrue(plantId);
        Map<Long, List<RouteStop>> patterns = routeStops
                .findByRouteIdInOrderByRouteIdAscSeqAsc(activeRoutes.stream().map(Route::getId).toList())
                .stream().collect(Collectors.groupingBy(RouteStop::getRouteId));

        List<JourneyOption> options = new ArrayList<>();
        for (Route route : activeRoutes) {
            List<RouteStop> pattern = patterns.getOrDefault(route.getId(), List.of());
            for (NearbyStop board : boardCandidates) {
                bestOnRoute(route, pattern, board, destination, now, zone, stopById)
                        .ifPresent(options::add);
            }
        }
        return options.stream()
                .sorted(Comparator.comparing(JourneyOption::arrivalTime))
                .limit(limit)
                .toList();
    }

    private java.util.Optional<JourneyOption> bestOnRoute(Route route, List<RouteStop> pattern, NearbyStop board,
                                                          Stop destination, Instant now, ZoneId zone,
                                                          Map<Long, Stop> stopById) {
        JourneyOption best = null;
        for (RouteStop boardRs : pattern) {
            if (!boardRs.getStopId().equals(board.stopId())) {
                continue;
            }
            for (RouteStop alightRs : pattern) {
                if (alightRs.getSeq() <= boardRs.getSeq() || !alightRs.getStopId().equals(destination.getId())) {
                    continue;
                }
                Instant readyAt = now.plus(Duration.ofMinutes(board.walkMinutes()));
                LocalDateTime readyLocal = LocalDateTime.ofInstant(readyAt, zone);
                List<LocalDateTime> next = ScheduleCalculator.nextArrivals(route, boardRs.getOffsetMinutes(),
                        readyLocal, 1);
                if (next.isEmpty()) {
                    continue;
                }
                Instant departure = next.get(0).atZone(zone).toInstant();
                Instant alight = departure.plus(Duration.ofMinutes(
                        alightRs.getOffsetMinutes() - boardRs.getOffsetMinutes()));
                JourneyOption option = new JourneyOption(route.getId(), route.getCode(), route.getName(), board,
                        destination.getId(), destination.getName(), departure, alight, minutesBetween(now, alight));
                if (best == null || option.arrivalTime().isBefore(best.arrivalTime())) {
                    best = option;
                }
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    private Map<Long, Stop> stopsById(Long plantId) {
        return stops.findByPlantIdOrderByIdAsc(plantId).stream()
                .collect(Collectors.toMap(Stop::getId, Function.identity()));
    }

    private ZoneId zone(Long plantId) {
        Plant plant = plantService.require(plantId);
        try {
            return ZoneId.of(plant.getTimezone());
        } catch (Exception e) {
            throw ApiException.badRequest("Plant timezone is invalid: " + plant.getTimezone());
        }
    }

    private static NearbyStop toNearby(Stop s, double lat, double lng) {
        int metres = (int) Math.round(GeoUtils.haversineKm(lat, lng, s.getLat(), s.getLng()) * 1000);
        return new NearbyStop(s.getId(), s.getCode(), s.getName(), s.getLat(), s.getLng(), metres,
                GeoUtils.walkMinutes(lat, lng, s.getLat(), s.getLng()));
    }

    private static long minutesBetween(Instant from, Instant to) {
        return Math.max(0, Duration.between(from, to).toMinutes());
    }
}
