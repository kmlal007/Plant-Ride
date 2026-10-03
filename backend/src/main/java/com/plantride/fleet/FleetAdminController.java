package com.plantride.fleet;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.common.ApiException;
import com.plantride.network.RouteRepository;
import com.plantride.org.DepartmentRepository;
import com.plantride.security.CurrentUser;

@RestController
@RequestMapping("/api/admin")
@Transactional
public class FleetAdminController {

    private final VendorRepository vendors;
    private final VehicleRepository vehicles;
    private final RateCardRepository rateCards;
    private final DepartmentRepository departments;
    private final RouteRepository routes;

    public FleetAdminController(VendorRepository vendors, VehicleRepository vehicles, RateCardRepository rateCards,
                                DepartmentRepository departments, RouteRepository routes) {
        this.vendors = vendors;
        this.vehicles = vehicles;
        this.rateCards = rateCards;
        this.departments = departments;
        this.routes = routes;
    }

    @GetMapping("/vendors")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Vendor> listVendors() {
        return vendors.findByPlantIdOrderByIdAsc(CurrentUser.get().plantId());
    }

    @PostMapping("/vendors")
    @PreAuthorize("hasRole('ADMIN')")
    public Vendor createVendor(@RequestBody Vendor body) {
        body.setId(null);
        body.setPlantId(CurrentUser.get().plantId());
        return vendors.save(body);
    }

    @PutMapping("/vendors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Vendor updateVendor(@PathVariable Long id, @RequestBody Vendor body) {
        Vendor v = vendors.require(id, CurrentUser.get().plantId(), "Vendor");
        v.setName(body.getName());
        v.setContactName(body.getContactName());
        v.setPhone(body.getPhone());
        v.setActive(body.isActive());
        return v;
    }

    /** Dispatchers also read vehicles for the live map. */
    @GetMapping("/vehicles")
    public List<Vehicle> listVehicles() {
        return vehicles.findByPlantIdOrderByIdAsc(CurrentUser.get().plantId());
    }

    @PostMapping("/vehicles")
    @PreAuthorize("hasRole('ADMIN')")
    public Vehicle createVehicle(@RequestBody Vehicle body) {
        Long plantId = CurrentUser.get().plantId();
        validateVehicle(body, plantId);
        Vehicle v = new Vehicle();
        v.setPlantId(plantId);
        copyVehicle(body, v);
        return vehicles.save(v);
    }

    @PutMapping("/vehicles/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Vehicle updateVehicle(@PathVariable Long id, @RequestBody Vehicle body) {
        Long plantId = CurrentUser.get().plantId();
        Vehicle v = vehicles.require(id, plantId, "Vehicle");
        validateVehicle(body, plantId);
        copyVehicle(body, v);
        if (!body.isActive() && (v.getStatus() == VehicleStatus.ASSIGNED || v.getStatus() == VehicleStatus.ON_TRIP)) {
            throw ApiException.conflict("Vehicle is on a ride; deactivate it after the ride ends");
        }
        if (!v.isActive()) {
            v.setStatus(VehicleStatus.BLOCKED);
            v.setCurrentDriverId(null);
        } else if (v.getStatus() == VehicleStatus.BLOCKED) {
            v.setStatus(VehicleStatus.OFF_DUTY);
        }
        return v;
    }

    private void validateVehicle(Vehicle body, Long plantId) {
        if (body.getRegistrationNo() == null || body.getRegistrationNo().isBlank()) {
            throw ApiException.badRequest("registrationNo is required");
        }
        if (body.getVehicleType() == null || body.getOwnerType() == null || body.getServiceMode() == null) {
            throw ApiException.badRequest("vehicleType, ownerType and serviceMode are required");
        }
        if (body.getCapacity() < 1) {
            throw ApiException.badRequest("capacity must be at least 1");
        }
        if (body.getOwnerType() == OwnerType.DEPARTMENT) {
            if (body.getOwnerDepartmentId() == null) {
                throw ApiException.badRequest("ownerDepartmentId is required for department-owned vehicles");
            }
            departments.require(body.getOwnerDepartmentId(), plantId, "Department");
        }
        if (body.getOwnerType() == OwnerType.VENDOR) {
            if (body.getVendorId() == null) {
                throw ApiException.badRequest("vendorId is required for vendor vehicles");
            }
            vendors.require(body.getVendorId(), plantId, "Vendor");
        }
        if (body.getRouteId() != null) {
            routes.require(body.getRouteId(), plantId, "Route");
        }
    }

    private static void copyVehicle(Vehicle from, Vehicle to) {
        to.setRegistrationNo(from.getRegistrationNo().trim().toUpperCase());
        to.setVehicleType(from.getVehicleType());
        to.setCapacity(from.getCapacity());
        to.setOwnerType(from.getOwnerType());
        to.setOwnerDepartmentId(from.getOwnerType() == OwnerType.DEPARTMENT ? from.getOwnerDepartmentId() : null);
        to.setVendorId(from.getOwnerType() == OwnerType.VENDOR ? from.getVendorId() : null);
        to.setServiceMode(from.getServiceMode());
        to.setRouteId(from.getServiceMode() == ServiceMode.FIXED_ROUTE ? from.getRouteId() : null);
        to.setGpsDeviceId(from.getGpsDeviceId() == null || from.getGpsDeviceId().isBlank()
                ? null : from.getGpsDeviceId().trim());
        to.setActive(from.isActive());
    }

    @GetMapping("/rate-cards")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RateCard> listRateCards() {
        return rateCards.findByPlantIdOrderByIdAsc(CurrentUser.get().plantId());
    }

    @PostMapping("/rate-cards")
    @PreAuthorize("hasRole('ADMIN')")
    public RateCard createRateCard(@RequestBody RateCard body) {
        validateRate(body);
        body.setId(null);
        body.setPlantId(CurrentUser.get().plantId());
        return rateCards.save(body);
    }

    @PutMapping("/rate-cards/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RateCard updateRateCard(@PathVariable Long id, @RequestBody RateCard body) {
        validateRate(body);
        RateCard r = rateCards.require(id, CurrentUser.get().plantId(), "Rate card");
        r.setVehicleType(body.getVehicleType());
        r.setRideType(body.getRideType());
        r.setBaseFare(body.getBaseFare());
        r.setPerKm(body.getPerKm());
        r.setPerMinute(body.getPerMinute());
        r.setMinimumFare(body.getMinimumFare());
        return r;
    }

    private static void validateRate(RateCard r) {
        if (r.getVehicleType() == null || r.getRideType() == null || r.getBaseFare() == null
                || r.getPerKm() == null || r.getPerMinute() == null || r.getMinimumFare() == null) {
            throw ApiException.badRequest("vehicleType, rideType, baseFare, perKm, perMinute and minimumFare are required");
        }
        if (r.getBaseFare().signum() < 0 || r.getPerKm().signum() < 0 || r.getPerMinute().signum() < 0
                || r.getMinimumFare().signum() < 0) {
            throw ApiException.badRequest("Rates cannot be negative");
        }
    }
}
