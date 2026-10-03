package com.plantride.tracking;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiclePositionRepository extends JpaRepository<VehiclePosition, Long> {

    List<VehiclePosition> findByVehicleIdAndFixTimeBetweenOrderByFixTimeAsc(Long vehicleId, Instant from, Instant to);
}
