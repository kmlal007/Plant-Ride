package com.plantride.ride;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.common.ApiException;
import com.plantride.common.GeoUtils;
import com.plantride.fleet.OwnerType;
import com.plantride.fleet.ServiceMode;
import com.plantride.fleet.Vehicle;
import com.plantride.fleet.VehicleRepository;
import com.plantride.fleet.VehicleStatus;
import com.plantride.notification.NotificationService;
import com.plantride.plant.Plant;
import com.plantride.plant.PlantService;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

/**
 * Picks a vehicle for a ride and offers it to that vehicle's driver.
 *
 * <p>Eligibility: on-demand, active, AVAILABLE, a driver signed on, a recent GPS fix within the plant's
 * dispatch radius, enough seats, matching vehicle type, and the driver has not already declined.
 * Department-owned vehicles serve only their own department. Ranking: the requester's own
 * department vehicles first, then by straight-line distance to pickup.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    /** Vehicles whose last GPS fix is older than this are not dispatched (position unknown). */
    static final Duration MAX_FIX_AGE = Duration.ofMinutes(10);

    private final VehicleRepository vehicles;
    private final AppUserRepository users;
    private final PlantService plantService;
    private final NotificationService notifications;
    private final Clock clock;

    public DispatchService(VehicleRepository vehicles, AppUserRepository users, PlantService plantService,
                           NotificationService notifications, Clock clock) {
        this.vehicles = vehicles;
        this.users = users;
        this.plantService = plantService;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Tries to offer the ride to the best eligible vehicle. Returns true if an offer was made. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean dispatch(RideRequest ride) {
        if (ride.getStatus() != RideStatus.SEARCHING) {
            return false;
        }
        Plant plant = plantService.require(ride.getPlantId());
        Long requesterDept = users.findById(ride.getRequesterId()).map(AppUser::getDepartmentId).orElse(null);

        for (Vehicle v : rankCandidates(ride, plant, requesterDept)) {
            if (vehicles.transition(v.getId(), VehicleStatus.AVAILABLE, VehicleStatus.ASSIGNED) == 1) {
                offer(ride, v, plant);
                return true;
            }
        }
        log.debug("No vehicle available for ride {}", ride.getId());
        return false;
    }

    /** Control-room override: offer the ride to a specific vehicle, skipping ranking rules. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assignManually(RideRequest ride, Long vehicleId) {
        Plant plant = plantService.require(ride.getPlantId());
        Vehicle v = vehicles.require(vehicleId, ride.getPlantId(), "Vehicle");
        if (v.getCurrentDriverId() == null) {
            throw ApiException.conflict("Vehicle has no driver on duty");
        }
        if (v.getCapacity() < ride.getPassengerCount()) {
            throw ApiException.conflict("Vehicle does not have enough seats");
        }
        if (vehicles.transition(v.getId(), VehicleStatus.AVAILABLE, VehicleStatus.ASSIGNED) != 1) {
            throw ApiException.conflict("Vehicle is not available");
        }
        offer(ride, v, plant);
    }

    List<Vehicle> rankCandidates(RideRequest ride, Plant plant, Long requesterDept) {
        Set<Long> declined = ride.rejectedDrivers();
        var cutoff = clock.instant().minus(MAX_FIX_AGE);
        return vehicles.findByPlantIdAndServiceModeAndStatusAndActiveTrue(plant.getId(), ServiceMode.ON_DEMAND,
                        VehicleStatus.AVAILABLE).stream()
                .filter(v -> v.getCurrentDriverId() != null && !declined.contains(v.getCurrentDriverId()))
                .filter(v -> v.getCapacity() >= ride.getPassengerCount())
                .filter(v -> ride.getVehicleType() == null || ride.getVehicleType() == v.getVehicleType())
                .filter(v -> v.getOwnerType() != OwnerType.DEPARTMENT
                        || Objects.equals(v.getOwnerDepartmentId(), requesterDept))
                .filter(v -> v.getLastFixAt() != null && v.getLastFixAt().isAfter(cutoff))
                .filter(v -> distanceKm(v, ride) <= plant.getDispatchRadiusKm())
                .sorted(Comparator
                        .comparing((Vehicle v) -> !(v.getOwnerType() == OwnerType.DEPARTMENT))
                        .thenComparingDouble(v -> distanceKm(v, ride)))
                .toList();
    }

    private void offer(RideRequest ride, Vehicle v, Plant plant) {
        ride.setStatus(RideStatus.OFFERED);
        ride.setVehicleId(v.getId());
        ride.setDriverId(v.getCurrentDriverId());
        ride.setOfferExpiresAt(clock.instant().plusSeconds(plant.getOfferTimeoutSeconds()));
        notifications.notifyUser(v.getCurrentDriverId(), "New ride request",
                "Pickup at " + ride.getPickupLabel() + " for " + ride.getPassengerCount() + " passenger(s)");
        log.info("Ride {} offered to vehicle {} driver {}", ride.getId(), v.getId(), v.getCurrentDriverId());
    }

    private static double distanceKm(Vehicle v, RideRequest ride) {
        return GeoUtils.haversineKm(v.getLastLat(), v.getLastLng(), ride.getPickupLat(), ride.getPickupLng());
    }
}
