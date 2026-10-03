package com.plantride.ride;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.plantride.common.PlantScopedRepository;

public interface RideRequestRepository extends PlantScopedRepository<RideRequest> {

    List<RideRequest> findTop50ByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<RideRequest> findByApproverIdAndStatusOrderByCreatedAtAsc(Long approverId, RideStatus status);

    Optional<RideRequest> findFirstByDriverIdAndStatusInOrderByCreatedAtDesc(Long driverId,
                                                                             Collection<RideStatus> statuses);

    List<RideRequest> findTop50ByDriverIdAndStatusOrderByCompletedAtDesc(Long driverId, RideStatus status);

    @Query("select r.id from RideRequest r where r.status in :statuses")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<RideStatus> statuses);

    @Query("select r from RideRequest r where r.plantId = :plantId and r.createdAt >= :from and r.createdAt < :to "
            + "and (:status is null or r.status = :status) order by r.createdAt desc")
    List<RideRequest> search(@Param("plantId") Long plantId, @Param("from") Instant from, @Param("to") Instant to,
                             @Param("status") RideStatus status);
}
