package com.plantride.ride;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.billing.RideCostingService;
import com.plantride.common.ApiException;
import com.plantride.common.GeoUtils;
import com.plantride.fleet.Vehicle;
import com.plantride.fleet.VehicleRepository;
import com.plantride.fleet.VehicleStatus;
import com.plantride.fleet.VehicleType;
import com.plantride.notification.NotificationService;
import com.plantride.org.CostCenter;
import com.plantride.org.CostCenterRepository;
import com.plantride.org.Project;
import com.plantride.org.ProjectRepository;
import com.plantride.plant.Plant;
import com.plantride.plant.PlantService;
import com.plantride.security.AuthUser;
import com.plantride.security.Role;
import com.plantride.tracking.TrackingService;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

/** Ride lifecycle: booking, approval, driver actions, completion and costing. */
@Service
@Transactional
public class RideService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    /** How long a driver waits at pickup before they may mark the rider as a no-show. */
    static final Duration NO_SHOW_WAIT = Duration.ofMinutes(5);
    static final Duration MAX_ADVANCE_BOOKING = Duration.ofDays(7);
    static final int MAX_PASSENGERS = 60;

    public record CreateRideRequest(RideType rideType, VehicleType vehicleType,
                                    String pickupLabel, Double pickupLat, Double pickupLng,
                                    String dropLabel, Double dropLat, Double dropLng,
                                    Instant scheduledAt, Integer passengerCount, String purpose,
                                    Long costCenterId, Long projectId,
                                    String visitorName, String visitorPhone, String gatePassRef) {
    }

    private final RideRequestRepository rides;
    private final AppUserRepository users;
    private final VehicleRepository vehicles;
    private final CostCenterRepository costCenters;
    private final ProjectRepository projects;
    private final RideCostingService rideCosting;
    private final PlantService plantService;
    private final DispatchService dispatchService;
    private final TrackingService trackingService;
    private final NotificationService notifications;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RideService(RideRequestRepository rides, AppUserRepository users, VehicleRepository vehicles,
                       CostCenterRepository costCenters, ProjectRepository projects, RideCostingService rideCosting,
                       PlantService plantService,
                       DispatchService dispatchService, TrackingService trackingService,
                       NotificationService notifications, Clock clock) {
        this.rides = rides;
        this.users = users;
        this.vehicles = vehicles;
        this.costCenters = costCenters;
        this.projects = projects;
        this.rideCosting = rideCosting;
        this.plantService = plantService;
        this.dispatchService = dispatchService;
        this.trackingService = trackingService;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- rider

    public RideView create(AuthUser me, CreateRideRequest req) {
        Plant plant = plantService.require(me.plantId());
        AppUser requester = users.require(me.userId(), me.plantId(), "User");
        Instant now = clock.instant();
        validateLocations(req);

        RideRequest ride = new RideRequest();
        ride.setPlantId(plant.getId());
        ride.setRequesterId(requester.getId());
        ride.setRideType(req.rideType() == null ? RideType.EXCLUSIVE : req.rideType());
        ride.setVehicleType(req.vehicleType());
        ride.setPickupLabel(req.pickupLabel().trim());
        ride.setPickupLat(req.pickupLat());
        ride.setPickupLng(req.pickupLng());
        ride.setDropLabel(req.dropLabel().trim());
        ride.setDropLat(req.dropLat());
        ride.setDropLng(req.dropLng());
        ride.setPassengerCount(req.passengerCount() == null ? 1 : req.passengerCount());
        if (ride.getPassengerCount() < 1 || ride.getPassengerCount() > MAX_PASSENGERS) {
            throw ApiException.badRequest("passengerCount must be between 1 and " + MAX_PASSENGERS);
        }
        ride.setPurpose(req.purpose());
        ride.setCreatedAt(now);
        ride.setOtp(String.format("%04d", random.nextInt(10_000)));

        if (req.scheduledAt() != null) {
            if (req.scheduledAt().isBefore(now.minus(Duration.ofMinutes(1)))) {
                throw ApiException.badRequest("scheduledAt is in the past");
            }
            if (req.scheduledAt().isAfter(now.plus(MAX_ADVANCE_BOOKING))) {
                throw ApiException.badRequest("Rides can be booked at most 7 days ahead");
            }
            ride.setScheduledAt(req.scheduledAt());
        }

        applyCharging(ride, requester, req.costCenterId(), req.projectId());
        applyVisitor(ride, plant, req);

        boolean needsApproval = ride.getRideType() == RideType.EXCLUSIVE
                && plant.isExclusiveRideRequiresApproval()
                && requester.getManagerId() != null;
        if (needsApproval) {
            ride.setStatus(RideStatus.PENDING_APPROVAL);
            ride.setApproverId(requester.getManagerId());
            rides.save(ride);
            notifications.notifyUser(requester.getManagerId(), "Ride approval needed",
                    requester.getName() + " requested an exclusive ride to " + ride.getDropLabel());
        } else {
            ride.setApprovedAt(now);
            // Persist first so the ride has an id; release() then decides SCHEDULED vs dispatch now.
            ride.setStatus(RideStatus.SCHEDULED);
            rides.save(ride);
            release(ride, plant);
        }
        if (ride.getVisitorPhone() != null) {
            notifications.sms(ride.getVisitorPhone(), "Your plant ride to " + ride.getDropLabel()
                    + " is booked. Share OTP " + ride.getOtp() + " with the driver at pickup.");
        }
        log.info("Ride {} created by user {} status {}", ride.getId(), requester.getId(), ride.getStatus());
        return view(ride, true);
    }

    @Transactional(readOnly = true)
    public List<RideView> myRides(AuthUser me) {
        return rides.findTop50ByRequesterIdOrderByCreatedAtDesc(me.userId()).stream()
                .map(r -> view(r, true)).toList();
    }

    @Transactional(readOnly = true)
    public RideView get(AuthUser me, Long rideId) {
        RideRequest ride = rides.require(rideId, me.plantId(), "Ride");
        boolean isRequester = ride.getRequesterId().equals(me.userId());
        boolean isDriver = ride.getDriverId() != null && ride.getDriverId().equals(me.userId());
        boolean isApprover = ride.getApproverId() != null && ride.getApproverId().equals(me.userId());
        boolean isStaff = me.role() == Role.ADMIN || me.role() == Role.DISPATCHER;
        if (!(isRequester || isDriver || isApprover || isStaff)) {
            throw ApiException.notFound("Ride");
        }
        return view(ride, isRequester);
    }

    public RideView cancel(AuthUser me, Long rideId, String reason) {
        RideRequest ride = rides.require(rideId, me.plantId(), "Ride");
        boolean isStaff = me.role() == Role.ADMIN || me.role() == Role.DISPATCHER;
        if (!ride.getRequesterId().equals(me.userId()) && !isStaff) {
            throw ApiException.notFound("Ride");
        }
        if (!RideStatus.CANCELLABLE.contains(ride.getStatus())) {
            throw ApiException.conflict("Ride cannot be cancelled in status " + ride.getStatus());
        }
        Long driverId = RideStatus.HOLDS_VEHICLE.contains(ride.getStatus()) ? ride.getDriverId() : null;
        releaseVehicle(ride);
        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(clock.instant());
        ride.setCancelReason(reason == null || reason.isBlank() ? "Cancelled by " + me.role() : reason);
        if (driverId != null) {
            notifications.notifyUser(driverId, "Ride cancelled", "Ride to " + ride.getDropLabel() + " was cancelled");
        }
        return view(ride, true);
    }

    // ---------------------------------------------------------------- approver

    @Transactional(readOnly = true)
    public List<RideView> pendingApprovals(AuthUser me) {
        return rides.findByApproverIdAndStatusOrderByCreatedAtAsc(me.userId(), RideStatus.PENDING_APPROVAL)
                .stream().map(r -> view(r, false)).toList();
    }

    public RideView decide(AuthUser me, Long rideId, boolean approve, String reason) {
        RideRequest ride = rides.require(rideId, me.plantId(), "Ride");
        if (!me.userId().equals(ride.getApproverId())) {
            throw ApiException.forbidden("You are not the approver of this ride");
        }
        if (ride.getStatus() != RideStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Ride is not waiting for approval");
        }
        if (approve) {
            ride.setApprovedAt(clock.instant());
            release(ride, plantService.require(ride.getPlantId()));
            notifications.notifyUser(ride.getRequesterId(), "Ride approved", "Your ride to " + ride.getDropLabel() + " was approved");
        } else {
            ride.setStatus(RideStatus.REJECTED);
            ride.setCancelReason(reason == null || reason.isBlank() ? "Rejected by approver" : reason);
            notifications.notifyUser(ride.getRequesterId(), "Ride rejected", ride.getCancelReason());
        }
        return view(ride, false);
    }

    // ---------------------------------------------------------------- driver

    @Transactional(readOnly = true)
    public RideView currentForDriver(AuthUser me) {
        return rides.findFirstByDriverIdAndStatusInOrderByCreatedAtDesc(me.userId(), RideStatus.HOLDS_VEHICLE)
                .map(r -> view(r, false)).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RideView> completedForDriver(AuthUser me) {
        return rides.findTop50ByDriverIdAndStatusOrderByCompletedAtDesc(me.userId(), RideStatus.COMPLETED)
                .stream().map(r -> view(r, false)).toList();
    }

    public RideView accept(AuthUser me, Long rideId) {
        RideRequest ride = driverRide(me, rideId, EnumSet.of(RideStatus.OFFERED));
        if (ride.getOfferExpiresAt() != null && clock.instant().isAfter(ride.getOfferExpiresAt())) {
            throw ApiException.conflict("Offer has expired");
        }
        ride.setStatus(RideStatus.ACCEPTED);
        ride.setAcceptedAt(clock.instant());
        ride.setOfferExpiresAt(null);
        notifications.notifyUser(ride.getRequesterId(), "Driver on the way", "Vehicle assigned for your ride");
        return view(ride, false);
    }

    /** Driver declines an offer, or gives up an accepted ride before pickup; the ride is re-dispatched. */
    public RideView decline(AuthUser me, Long rideId) {
        RideRequest ride = driverRide(me, rideId, EnumSet.of(RideStatus.OFFERED, RideStatus.ACCEPTED));
        redispatch(ride);
        return view(ride, false);
    }

    public RideView arrive(AuthUser me, Long rideId) {
        RideRequest ride = driverRide(me, rideId, EnumSet.of(RideStatus.ACCEPTED));
        ride.setStatus(RideStatus.DRIVER_ARRIVED);
        ride.setArrivedAt(clock.instant());
        notifications.notifyUser(ride.getRequesterId(), "Driver arrived", "Your vehicle is at " + ride.getPickupLabel());
        return view(ride, false);
    }

    public RideView start(AuthUser me, Long rideId, String otp) {
        RideRequest ride = driverRide(me, rideId, EnumSet.of(RideStatus.DRIVER_ARRIVED));
        if (otp == null || !java.security.MessageDigest.isEqual(otp.trim().getBytes(), ride.getOtp().getBytes())) {
            throw ApiException.badRequest("Incorrect OTP");
        }
        if (vehicles.transition(ride.getVehicleId(), VehicleStatus.ASSIGNED, VehicleStatus.ON_TRIP) != 1) {
            throw ApiException.conflict("Vehicle is not in an assignable state");
        }
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(clock.instant());
        return view(ride, false);
    }

    public RideView complete(AuthUser me, Long rideId) {
        RideRequest ride = driverRide(me, rideId, EnumSet.of(RideStatus.IN_PROGRESS));
        Instant now = clock.instant();
        Vehicle vehicle = vehicles.findById(ride.getVehicleId()).orElseThrow();

        BigDecimal km = trackingService.measuredDistanceKm(vehicle.getId(), ride.getStartedAt(), now);
        if (km != null && km.signum() > 0) {
            ride.setDistanceSource("GPS");
        } else {
            double estimate = GeoUtils.haversineKm(ride.getPickupLat(), ride.getPickupLng(),
                    ride.getDropLat(), ride.getDropLng()) * GeoUtils.ROAD_DETOUR_FACTOR;
            km = BigDecimal.valueOf(estimate).setScale(2, RoundingMode.HALF_UP);
            ride.setDistanceSource("ESTIMATED");
        }
        int minutes = (int) Math.max(1, Math.ceil(Duration.between(ride.getStartedAt(), now).toSeconds() / 60.0));
        ride.setDistanceKm(km);
        ride.setDurationMinutes(minutes);
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(now);

        rideCosting.cost(ride, vehicle, now);

        vehicles.transition(vehicle.getId(), VehicleStatus.ON_TRIP, VehicleStatus.AVAILABLE);
        notifications.notifyUser(ride.getRequesterId(), "Ride completed", "You have reached " + ride.getDropLabel());
        return view(ride, false);
    }

    public RideView noShow(AuthUser me, Long rideId) {
        RideRequest ride = driverRide(me, rideId, EnumSet.of(RideStatus.DRIVER_ARRIVED));
        if (clock.instant().isBefore(ride.getArrivedAt().plus(NO_SHOW_WAIT))) {
            throw ApiException.conflict("Wait at least " + NO_SHOW_WAIT.toMinutes() + " minutes before marking no-show");
        }
        releaseVehicle(ride);
        ride.setStatus(RideStatus.NO_SHOW);
        notifications.notifyUser(ride.getRequesterId(), "Marked as no-show",
                "The driver waited at " + ride.getPickupLabel() + " but could not find you");
        return view(ride, false);
    }

    // ---------------------------------------------------------------- control room

    public RideView assignManually(Long plantId, Long rideId, Long vehicleId) {
        RideRequest ride = rides.require(rideId, plantId, "Ride");
        if (ride.getStatus() != RideStatus.SEARCHING && ride.getStatus() != RideStatus.UNFULFILLED) {
            throw ApiException.conflict("Only searching or unfulfilled rides can be assigned manually");
        }
        ride.setStatus(RideStatus.SEARCHING);
        if (ride.getSearchStartedAt() == null) {
            ride.setSearchStartedAt(clock.instant());
        }
        dispatchService.assignManually(ride, vehicleId);
        return view(ride, false);
    }

    @Transactional(readOnly = true)
    public List<RideView> search(Long plantId, Instant from, Instant to, RideStatus status) {
        return rides.search(plantId, from, to, status).stream().map(r -> view(r, false)).toList();
    }

    // ---------------------------------------------------------------- background sweep

    /** Advances time-driven transitions of one ride. Called by {@link RideSweeper} in its own transaction. */
    public void sweep(Long rideId) {
        RideRequest ride = rides.findById(rideId).orElse(null);
        if (ride == null) {
            return;
        }
        Plant plant = plantService.require(ride.getPlantId());
        Instant now = clock.instant();
        switch (ride.getStatus()) {
            case OFFERED -> {
                if (ride.getOfferExpiresAt() != null && now.isAfter(ride.getOfferExpiresAt())) {
                    log.info("Offer for ride {} to driver {} expired", ride.getId(), ride.getDriverId());
                    redispatch(ride);
                }
            }
            case SCHEDULED -> {
                if (!now.isBefore(ride.getScheduledAt().minus(Duration.ofMinutes(plant.getScheduledDispatchLeadMinutes())))) {
                    startSearch(ride, now);
                }
            }
            case SEARCHING -> {
                if (now.isAfter(ride.getSearchStartedAt().plus(Duration.ofMinutes(plant.getSearchTimeoutMinutes())))) {
                    ride.setStatus(RideStatus.UNFULFILLED);
                    log.warn("Ride {} unfulfilled after {} min", ride.getId(), plant.getSearchTimeoutMinutes());
                    notifications.notifyUser(ride.getRequesterId(), "No vehicle available",
                            "We could not find a vehicle yet. The transport control room has been alerted.");
                } else {
                    dispatchService.dispatch(ride);
                }
            }
            default -> {
                // Nothing time-driven in other states.
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Moves an approved ride to SCHEDULED or straight into dispatch. */
    private void release(RideRequest ride, Plant plant) {
        Instant now = clock.instant();
        boolean later = ride.getScheduledAt() != null && ride.getScheduledAt()
                .minus(Duration.ofMinutes(plant.getScheduledDispatchLeadMinutes())).isAfter(now);
        if (later) {
            ride.setStatus(RideStatus.SCHEDULED);
        } else {
            startSearch(ride, now);
        }
    }

    private void startSearch(RideRequest ride, Instant now) {
        ride.setStatus(RideStatus.SEARCHING);
        ride.setSearchStartedAt(now);
        dispatchService.dispatch(ride);
    }

    private void redispatch(RideRequest ride) {
        ride.addRejectedDriver(ride.getDriverId());
        releaseVehicle(ride);
        ride.setVehicleId(null);
        ride.setDriverId(null);
        ride.setStatus(RideStatus.SEARCHING);
        ride.setAcceptedAt(null);
        ride.setOfferExpiresAt(null);
        dispatchService.dispatch(ride);
    }

    private void releaseVehicle(RideRequest ride) {
        if (ride.getVehicleId() != null && RideStatus.HOLDS_VEHICLE.contains(ride.getStatus())) {
            VehicleStatus held = ride.getStatus() == RideStatus.IN_PROGRESS ? VehicleStatus.ON_TRIP : VehicleStatus.ASSIGNED;
            vehicles.transition(ride.getVehicleId(), held, VehicleStatus.AVAILABLE);
        }
        // vehicleId/driverId are kept on cancelled and no-show rides for audit and vendor billing.
    }

    private RideRequest driverRide(AuthUser me, Long rideId, EnumSet<RideStatus> allowed) {
        RideRequest ride = rides.require(rideId, me.plantId(), "Ride");
        if (!me.userId().equals(ride.getDriverId())) {
            throw ApiException.notFound("Ride");
        }
        if (!allowed.contains(ride.getStatus())) {
            throw ApiException.conflict("Action not allowed in status " + ride.getStatus());
        }
        return ride;
    }

    private void applyCharging(RideRequest ride, AppUser requester, Long costCenterId, Long projectId) {
        Long plantId = ride.getPlantId();
        if (projectId != null) {
            Project project = projects.require(projectId, plantId, "Project");
            if (!project.isActive()) {
                throw ApiException.badRequest("Project is not active");
            }
            ride.setProjectId(project.getId());
            ride.setCostCenterId(project.getCostCenterId());
            return;
        }
        Long ccId = costCenterId != null ? costCenterId : requester.getDefaultCostCenterId();
        if (ccId == null) {
            throw ApiException.badRequest("Select a cost center or project to charge this ride to");
        }
        CostCenter cc = costCenters.require(ccId, plantId, "Cost center");
        if (!cc.isActive()) {
            throw ApiException.badRequest("Cost center is not active");
        }
        ride.setCostCenterId(cc.getId());
    }

    private static void applyVisitor(RideRequest ride, Plant plant, CreateRideRequest req) {
        boolean hasVisitor = notBlank(req.visitorName()) || notBlank(req.visitorPhone()) || notBlank(req.gatePassRef());
        if (!hasVisitor) {
            return;
        }
        if (!plant.isVisitorModuleEnabled()) {
            throw ApiException.badRequest("Visitor bookings are not enabled for this plant");
        }
        if (!notBlank(req.visitorName()) || !notBlank(req.visitorPhone())) {
            throw ApiException.badRequest("visitorName and visitorPhone are required for visitor rides");
        }
        ride.setVisitorName(req.visitorName().trim());
        ride.setVisitorPhone(req.visitorPhone().trim());
        ride.setGatePassRef(notBlank(req.gatePassRef()) ? req.gatePassRef().trim() : null);
    }

    private static void validateLocations(CreateRideRequest req) {
        if (!notBlank(req.pickupLabel()) || !notBlank(req.dropLabel())
                || req.pickupLat() == null || req.pickupLng() == null
                || req.dropLat() == null || req.dropLng() == null) {
            throw ApiException.badRequest("Pickup and drop label and coordinates are required");
        }
        if (GeoUtils.haversineKm(req.pickupLat(), req.pickupLng(), req.dropLat(), req.dropLng()) < 0.05) {
            throw ApiException.badRequest("Pickup and drop are the same place");
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private RideView view(RideRequest r, boolean includeOtp) {
        AppUser requester = users.findById(r.getRequesterId()).orElse(null);
        Vehicle vehicle = r.getVehicleId() == null ? null : vehicles.findById(r.getVehicleId()).orElse(null);
        AppUser driver = r.getDriverId() == null ? null : users.findById(r.getDriverId()).orElse(null);
        return RideView.of(r, requester, vehicle, driver, includeOtp);
    }
}
