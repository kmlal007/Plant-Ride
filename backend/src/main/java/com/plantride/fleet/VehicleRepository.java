package com.plantride.fleet;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.plantride.common.PlantScopedRepository;

/**
 * Status changes go through conditional bulk updates: each returns 1 only for the caller that won
 * the race, so two concurrent dispatches can never assign the same vehicle. {@link Vehicle} uses
 * dynamic updates, so position updates on a managed entity never overwrite the status column.
 */
public interface VehicleRepository extends PlantScopedRepository<Vehicle> {

    Optional<Vehicle> findByGpsDeviceId(String gpsDeviceId);

    Optional<Vehicle> findByCurrentDriverId(Long driverId);

    List<Vehicle> findByPlantIdAndServiceModeAndStatusAndActiveTrue(Long plantId, ServiceMode mode,
                                                                    VehicleStatus status);

    List<Vehicle> findByRouteIdAndActiveTrue(Long routeId);

    @Modifying(flushAutomatically = true)
    @Query("update Vehicle v set v.status = :to where v.id = :id and v.status = :from")
    int transition(@Param("id") Long id, @Param("from") VehicleStatus from, @Param("to") VehicleStatus to);

    @Modifying(flushAutomatically = true)
    @Query("update Vehicle v set v.status = com.plantride.fleet.VehicleStatus.AVAILABLE, v.currentDriverId = :driverId "
            + "where v.id = :id and v.status = com.plantride.fleet.VehicleStatus.OFF_DUTY and v.currentDriverId is null")
    int signOn(@Param("id") Long id, @Param("driverId") Long driverId);

    @Modifying(flushAutomatically = true)
    @Query("update Vehicle v set v.status = com.plantride.fleet.VehicleStatus.OFF_DUTY, v.currentDriverId = null "
            + "where v.id = :id and v.status = com.plantride.fleet.VehicleStatus.AVAILABLE and v.currentDriverId = :driverId")
    int signOff(@Param("id") Long id, @Param("driverId") Long driverId);
}
