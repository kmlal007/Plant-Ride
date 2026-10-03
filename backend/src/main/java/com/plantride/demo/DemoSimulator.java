package com.plantride.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.plantride.common.GeoUtils;
import com.plantride.fleet.ServiceMode;
import com.plantride.fleet.Vehicle;
import com.plantride.fleet.VehicleRepository;
import com.plantride.network.Route;
import com.plantride.network.RouteRepository;
import com.plantride.network.RouteStop;
import com.plantride.network.RouteStopRepository;
import com.plantride.network.ScheduleCalculator;
import com.plantride.network.Stop;
import com.plantride.network.StopRepository;
import com.plantride.plant.PlantService;
import com.plantride.ride.RideRequest;
import com.plantride.ride.RideRequestRepository;
import com.plantride.ride.RideService;
import com.plantride.ride.RideStatus;
import com.plantride.security.AuthUser;
import com.plantride.security.Role;
import com.plantride.tracking.TrackingService;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

/**
 * Brings the pilot plant to life for showcases. Every tick it:
 * <ul>
 *   <li>moves shuttles/buses (GPS id {@code SIM-*}) along their route per the timetable,</li>
 *   <li>drives on-demand {@code SIM-*} vehicles towards the pickup or drop of their current ride,</li>
 *   <li>lets "bot" drivers accept, arrive, start (with the real OTP) and complete rides.</li>
 * </ul>
 * Positions go through the normal tracking pipeline, so live ETAs, GPS-measured fares and over-speed
 * checks behave exactly as with real devices.
 */
@Component
@ConditionalOnProperty(name = "plantride.demo.simulator", havingValue = "true")
public class DemoSimulator {

    private static final Logger log = LoggerFactory.getLogger(DemoSimulator.class);
    private static final long TICK_MS = 5000;
    private static final double ARRIVAL_METRES = 30;
    private static final Duration BOT_ACCEPT_DELAY = Duration.ofSeconds(8);
    private static final Duration BOT_BOARDING_TIME = Duration.ofSeconds(20);
    private static final Set<RideStatus> ACTIVE = EnumSet.of(RideStatus.OFFERED, RideStatus.ACCEPTED,
            RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS);

    private final DemoProperties props;
    private final VehicleRepository vehicles;
    private final RouteRepository routes;
    private final RouteStopRepository routeStops;
    private final StopRepository stops;
    private final RideRequestRepository rides;
    private final AppUserRepository users;
    private final RideService rideService;
    private final TrackingService tracking;
    private final PlantService plantService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DemoSimulator(DemoProperties props, VehicleRepository vehicles, RouteRepository routes,
                         RouteStopRepository routeStops, StopRepository stops, RideRequestRepository rides,
                         AppUserRepository users, RideService rideService, TrackingService tracking,
                         PlantService plantService, TransactionTemplate tx, Clock clock) {
        this.props = props;
        this.vehicles = vehicles;
        this.routes = routes;
        this.routeStops = routeStops;
        this.stops = stops;
        this.rides = rides;
        this.users = users;
        this.rideService = rideService;
        this.tracking = tracking;
        this.plantService = plantService;
        this.tx = tx;
        this.clock = clock;
        log.warn("Demo simulator is ON (bot drivers: {})", props.botDriverLogins());
    }

    @Scheduled(fixedDelay = TICK_MS, initialDelay = 3000)
    public void tick() {
        List<Vehicle> simulated = vehicles.findAll().stream()
                .filter(v -> v.isActive() && v.getGpsDeviceId() != null && v.getGpsDeviceId().startsWith("SIM-"))
                .toList();
        Set<Long> botDriverIds = botDriverIds();
        for (Vehicle v : simulated) {
            try {
                if (v.getServiceMode() == ServiceMode.FIXED_ROUTE) {
                    moveOnRoute(v);
                } else if (v.getCurrentDriverId() != null) {
                    moveOnDemand(v, botDriverIds.contains(v.getCurrentDriverId()));
                }
            } catch (RuntimeException e) {
                log.debug("Simulator step for vehicle {} failed: {}", v.getId(), e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- fixed route

    private void moveOnRoute(Vehicle v) {
        if (v.getRouteId() == null) {
            return;
        }
        Route route = routes.findById(v.getRouteId()).orElse(null);
        if (route == null || !route.isActive()) {
            return;
        }
        List<RouteStop> pattern = routeStops.findByRouteIdOrderBySeqAsc(route.getId());
        if (pattern.size() < 2) {
            return;
        }
        Map<Long, Stop> stopById = stops.findAllById(pattern.stream().map(RouteStop::getStopId).toList()).stream()
                .collect(Collectors.toMap(Stop::getId, Function.identity()));
        List<Vehicle> fleetOnRoute = vehicles.findByRouteIdAndActiveTrue(route.getId()).stream()
                .sorted(Comparator.comparing(Vehicle::getId)).toList();
        int index = fleetOnRoute.indexOf(fleetOnRoute.stream().filter(x -> x.getId().equals(v.getId())).findFirst()
                .orElse(v));

        ZoneId zone = ZoneId.of(plantService.require(v.getPlantId()).getTimezone());
        Instant now = clock.instant();
        LocalDateTime local = LocalDateTime.ofInstant(now, zone);
        int tripMinutes = pattern.get(pattern.size() - 1).getOffsetMinutes();
        LocalDateTime windowStart = local.minusMinutes(tripMinutes + (long) route.getHeadwayMinutes() * (index + 1));
        List<LocalDateTime> departed = ScheduleCalculator.nextArrivals(route, 0, windowStart, 500).stream()
                .filter(d -> !d.isAfter(local)).toList();
        if (departed.size() <= index) {
            return; // outside service hours: stay silent so riders don't see a stale "live" bus
        }
        LocalDateTime departure = departed.get(departed.size() - 1 - index);
        double elapsed = Duration.between(departure, local).toSeconds() / 60.0;
        Stop first = stopById.get(pattern.get(0).getStopId());
        if (elapsed > tripMinutes) {
            send(v, first.getLat(), first.getLng(), 0); // waiting at the terminus for its next trip
            return;
        }
        for (int i = 1; i < pattern.size(); i++) {
            RouteStop a = pattern.get(i - 1);
            RouteStop b = pattern.get(i);
            if (elapsed <= b.getOffsetMinutes()) {
                Stop sa = stopById.get(a.getStopId());
                Stop sb = stopById.get(b.getStopId());
                double span = Math.max(1, b.getOffsetMinutes() - a.getOffsetMinutes());
                double f = Math.max(0, Math.min(1, (elapsed - a.getOffsetMinutes()) / span));
                double kmh = GeoUtils.haversineKm(sa.getLat(), sa.getLng(), sb.getLat(), sb.getLng())
                        * GeoUtils.ROAD_DETOUR_FACTOR / (span / 60.0);
                send(v, sa.getLat() + (sb.getLat() - sa.getLat()) * f, sa.getLng() + (sb.getLng() - sa.getLng()) * f,
                        Math.min(kmh, 28));
                return;
            }
        }
    }

    // ---------------------------------------------------------------- on demand

    private void moveOnDemand(Vehicle v, boolean bot) {
        Optional<RideRequest> current = rides.findFirstByDriverIdAndStatusInOrderByCreatedAtDesc(
                v.getCurrentDriverId(), ACTIVE);
        double lat = v.getLastLat() == null ? PilotDemoSeeder.LAT : v.getLastLat();
        double lng = v.getLastLng() == null ? PilotDemoSeeder.LNG : v.getLastLng();
        if (current.isEmpty() || !v.getId().equals(current.get().getVehicleId())) {
            send(v, lat, lng, 0); // idle but on duty: keep the fix fresh so it stays dispatchable
            return;
        }
        RideRequest ride = current.get();
        Instant now = clock.instant();
        double[] target = switch (ride.getStatus()) {
            case ACCEPTED -> new double[] {ride.getPickupLat(), ride.getPickupLng()};
            case IN_PROGRESS -> new double[] {ride.getDropLat(), ride.getDropLng()};
            default -> null;
        };
        boolean atTarget = false;
        if (target == null) {
            send(v, lat, lng, 0);
        } else {
            double distKm = GeoUtils.haversineKm(lat, lng, target[0], target[1]);
            double stepKm = props.speedKmh() * TICK_MS / 3_600_000.0;
            if (distKm <= stepKm) {
                send(v, target[0], target[1], props.speedKmh() * distKm / stepKm);
                atTarget = true;
            } else {
                double f = stepKm / distKm;
                send(v, lat + (target[0] - lat) * f, lng + (target[1] - lng) * f, props.speedKmh());
            }
            atTarget = atTarget || distKm * 1000 <= ARRIVAL_METRES;
        }
        if (!bot) {
            return;
        }
        AuthUser driver = new AuthUser(v.getCurrentDriverId(), v.getPlantId(), Role.DRIVER);
        switch (ride.getStatus()) {
            case OFFERED -> {
                Instant offeredAt = ride.getOfferExpiresAt()
                        .minusSeconds(plantService.require(v.getPlantId()).getOfferTimeoutSeconds());
                if (now.isAfter(offeredAt.plus(BOT_ACCEPT_DELAY))) {
                    botAction(() -> rideService.accept(driver, ride.getId()));
                }
            }
            case ACCEPTED -> {
                if (atTarget) {
                    botAction(() -> rideService.arrive(driver, ride.getId()));
                }
            }
            case DRIVER_ARRIVED -> {
                if (now.isAfter(ride.getArrivedAt().plus(BOT_BOARDING_TIME))) {
                    botAction(() -> rideService.start(driver, ride.getId(), ride.getOtp()));
                }
            }
            case IN_PROGRESS -> {
                if (atTarget) {
                    botAction(() -> rideService.complete(driver, ride.getId()));
                }
            }
            default -> {
            }
        }
    }

    private void botAction(Runnable action) {
        try {
            tx.executeWithoutResult(s -> action.run());
        } catch (RuntimeException e) {
            log.debug("Bot driver action skipped: {}", e.getMessage());
        }
    }

    private void send(Vehicle v, double lat, double lng, double kmh) {
        tracking.ingest(v.getGpsDeviceId(), lat, lng, kmh, clock.instant());
    }

    private Set<Long> botDriverIds() {
        if (props.botDriverLogins() == null) {
            return Set.of();
        }
        return props.botDriverLogins().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(users::findByLoginId)
                .flatMap(Optional::stream)
                .map(AppUser::getId)
                .collect(Collectors.toSet());
    }
}
