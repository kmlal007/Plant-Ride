package com.plantride.fleet;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.common.ApiException;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

/** A driver signs on to a vehicle to make it dispatchable, and signs off at shift end. */
@Service
public class DriverDutyService {

    private final VehicleRepository vehicles;
    private final AppUserRepository users;

    public DriverDutyService(VehicleRepository vehicles, AppUserRepository users) {
        this.vehicles = vehicles;
        this.users = users;
    }

    /** Vehicles the driver may sign on to: active on-demand vehicles that are free, of the driver's vendor. */
    public List<Vehicle> eligibleVehicles(Long driverId, Long plantId) {
        AppUser driver = users.require(driverId, plantId, "Driver");
        return vehicles.findByPlantIdAndServiceModeAndStatusAndActiveTrue(plantId, ServiceMode.ON_DEMAND,
                        VehicleStatus.OFF_DUTY).stream()
                .filter(v -> driver.getVendorId() == null || driver.getVendorId().equals(v.getVendorId()))
                .toList();
    }

    @Transactional
    public Vehicle startDuty(Long driverId, Long plantId, Long vehicleId) {
        if (vehicles.findByCurrentDriverId(driverId).isPresent()) {
            throw ApiException.conflict("You are already on duty with a vehicle");
        }
        Vehicle vehicle = vehicles.require(vehicleId, plantId, "Vehicle");
        if (!vehicle.isActive() || vehicle.getServiceMode() != ServiceMode.ON_DEMAND) {
            throw ApiException.badRequest("Vehicle is not available for on-demand duty");
        }
        if (eligibleVehicles(driverId, plantId).stream().noneMatch(v -> v.getId().equals(vehicleId))) {
            throw ApiException.conflict("Vehicle is already in use or belongs to another vendor");
        }
        if (vehicles.signOn(vehicleId, driverId) != 1) {
            throw ApiException.conflict("Vehicle was just taken by another driver");
        }
        // Keep the already-loaded entity consistent with the row we just updated.
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicle.setCurrentDriverId(driverId);
        return vehicle;
    }

    @Transactional
    public void endDuty(Long driverId) {
        Vehicle vehicle = vehicles.findByCurrentDriverId(driverId)
                .orElseThrow(() -> ApiException.badRequest("You are not on duty"));
        if (vehicles.signOff(vehicle.getId(), driverId) != 1) {
            throw ApiException.conflict("Finish or hand over your current ride before ending duty");
        }
    }

    public Vehicle currentVehicle(Long driverId) {
        return vehicles.findByCurrentDriverId(driverId).orElse(null);
    }
}
