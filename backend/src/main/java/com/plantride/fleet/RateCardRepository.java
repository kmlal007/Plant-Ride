package com.plantride.fleet;

import java.util.Optional;

import com.plantride.common.PlantScopedRepository;
import com.plantride.ride.RideType;

public interface RateCardRepository extends PlantScopedRepository<RateCard> {

    Optional<RateCard> findByPlantIdAndVehicleTypeAndRideType(Long plantId, VehicleType vehicleType, RideType rideType);
}
