package com.plantride.ride;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.plantride.common.PlantScopedRepository;

public interface CostAllocationRepository extends PlantScopedRepository<CostAllocation> {

    /** rides counts ride-related rows only; amount includes shuttle allocations and credits. */
    record CostCenterTotal(Long costCenterId, Long rides, BigDecimal amount) {
    }

    List<CostAllocation> findByRideRequestId(Long rideRequestId);

    boolean existsByPlantIdAndPeriodAndBasisIn(Long plantId, String period, List<String> bases);

    @Query("select new com.plantride.ride.CostAllocationRepository$CostCenterTotal(a.costCenterId, count(a.rideRequestId), sum(a.amount)) "
            + "from CostAllocation a where a.plantId = :plantId and a.allocatedAt >= :from and a.allocatedAt < :to "
            + "group by a.costCenterId")
    List<CostCenterTotal> totalsByCostCenter(@Param("plantId") Long plantId, @Param("from") Instant from,
                                             @Param("to") Instant to);
}
