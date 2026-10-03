package com.plantride.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.billing.CostPolicies.LentVehicleChargeMode;
import com.plantride.billing.CostPolicies.OwnVehicleChargeMode;
import com.plantride.fleet.FareCalculator;
import com.plantride.fleet.OwnerType;
import com.plantride.fleet.RateCard;
import com.plantride.fleet.RateCardRepository;
import com.plantride.fleet.Vehicle;
import com.plantride.org.CostCenter;
import com.plantride.org.CostCenterRepository;
import com.plantride.plant.Plant;
import com.plantride.plant.PlantService;
import com.plantride.ride.CostAllocation;
import com.plantride.ride.CostAllocationRepository;
import com.plantride.ride.RideRequest;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

/**
 * Prices a completed ride from the rate card and writes cost allocation rows according to the plant's
 * cost policies for department-owned vehicles.
 */
@Service
public class RideCostingService {

    private static final Logger log = LoggerFactory.getLogger(RideCostingService.class);

    private final RateCardRepository rateCards;
    private final CostAllocationRepository allocations;
    private final CostCenterRepository costCenters;
    private final AppUserRepository users;
    private final PlantService plantService;

    public RideCostingService(RateCardRepository rateCards, CostAllocationRepository allocations,
                              CostCenterRepository costCenters, AppUserRepository users, PlantService plantService) {
        this.rateCards = rateCards;
        this.allocations = allocations;
        this.costCenters = costCenters;
        this.users = users;
        this.plantService = plantService;
    }

    /** Sets {@code ride.fare} and records allocations. Leaves the ride uncosted if no rate card exists. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cost(RideRequest ride, Vehicle vehicle, Instant at) {
        RateCard rate = rateCards.findByPlantIdAndVehicleTypeAndRideType(ride.getPlantId(), vehicle.getVehicleType(),
                ride.getRideType()).orElse(null);
        if (rate == null) {
            log.warn("No rate card for plant {} {} {}: ride {} left uncosted",
                    ride.getPlantId(), vehicle.getVehicleType(), ride.getRideType(), ride.getId());
            return;
        }
        BigDecimal fare = FareCalculator.fare(rate, ride.getDistanceKm(), ride.getDurationMinutes());
        ride.setFare(fare);

        Plant plant = plantService.require(ride.getPlantId());
        if (vehicle.getOwnerType() != OwnerType.DEPARTMENT) {
            allocate(ride, ride.getCostCenterId(), ride.getProjectId(), fare, CostAllocation.BASIS_RIDE_FARE, at);
            return;
        }
        Long requesterDept = users.findById(ride.getRequesterId()).map(AppUser::getDepartmentId).orElse(null);
        if (Objects.equals(vehicle.getOwnerDepartmentId(), requesterDept)) {
            BigDecimal amount = plant.getOwnVehicleChargeMode() == OwnVehicleChargeMode.CHARGE ? fare : BigDecimal.ZERO;
            allocate(ride, ride.getCostCenterId(), ride.getProjectId(), amount, CostAllocation.BASIS_OWN_VEHICLE, at);
            return;
        }
        LentVehicleChargeMode mode = plant.getLentVehicleChargeMode();
        BigDecimal charge = mode == LentVehicleChargeMode.NO_CHARGE ? BigDecimal.ZERO : fare;
        allocate(ride, ride.getCostCenterId(), ride.getProjectId(), charge, CostAllocation.BASIS_LENT_VEHICLE_USE, at);
        if (mode == LentVehicleChargeMode.CHARGE_BOOKER_CREDIT_OWNER) {
            ownerCostCenter(vehicle).ifPresentOrElse(
                    cc -> allocate(ride, cc.getId(), null, fare.negate(), CostAllocation.BASIS_LENT_VEHICLE_CREDIT, at),
                    () -> log.warn("Owner department {} of vehicle {} has no active cost center; credit skipped",
                            vehicle.getOwnerDepartmentId(), vehicle.getId()));
        }
    }

    private java.util.Optional<CostCenter> ownerCostCenter(Vehicle vehicle) {
        return costCenters.findByPlantIdOrderByIdAsc(vehicle.getPlantId()).stream()
                .filter(cc -> cc.isActive() && Objects.equals(cc.getDepartmentId(), vehicle.getOwnerDepartmentId()))
                .findFirst();
    }

    private void allocate(RideRequest ride, Long costCenterId, Long projectId, BigDecimal amount, String basis,
                          Instant at) {
        CostAllocation a = new CostAllocation();
        a.setPlantId(ride.getPlantId());
        a.setRideRequestId(ride.getId());
        a.setCostCenterId(costCenterId);
        a.setProjectId(projectId);
        a.setAmount(amount);
        a.setBasis(basis);
        a.setAllocatedAt(at);
        allocations.save(a);
    }
}
