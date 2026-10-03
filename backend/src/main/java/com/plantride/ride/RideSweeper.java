package com.plantride.ride;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drives time-based ride transitions: expired driver offers, scheduled rides becoming due,
 * retrying dispatch, and search timeouts. Each ride is processed in its own transaction so one
 * failure (e.g. a concurrent driver action) does not block the others.
 *
 * <p>Runs on every backend instance; correctness under concurrency relies on optimistic locking of
 * rides and conditional vehicle updates. With many instances, add a scheduler lock (e.g. ShedLock).
 */
@Component
public class RideSweeper {

    private static final Logger log = LoggerFactory.getLogger(RideSweeper.class);

    private final RideRequestRepository rides;
    private final RideService rideService;
    private final TransactionTemplate tx;

    public RideSweeper(RideRequestRepository rides, RideService rideService, TransactionTemplate tx) {
        this.rides = rides;
        this.rideService = rideService;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${plantride.sweeper-interval-ms}")
    public void run() {
        for (Long id : rides.findIdsByStatusIn(EnumSet.of(RideStatus.OFFERED, RideStatus.SCHEDULED, RideStatus.SEARCHING))) {
            try {
                tx.executeWithoutResult(status -> rideService.sweep(id));
            } catch (RuntimeException e) {
                log.warn("Sweep of ride {} failed: {}", id, e.getMessage());
            }
        }
    }
}
