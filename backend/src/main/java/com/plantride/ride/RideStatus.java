package com.plantride.ride;

import java.util.EnumSet;
import java.util.Set;

public enum RideStatus {
    PENDING_APPROVAL,
    /** Approved, waiting until shortly before the scheduled pickup time to dispatch. */
    SCHEDULED,
    /** Looking for an available vehicle. */
    SEARCHING,
    /** Offered to one driver who must accept before the offer expires. */
    OFFERED,
    ACCEPTED,
    DRIVER_ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    REJECTED,
    NO_SHOW,
    /** No vehicle found within the search timeout; needs the control room. */
    UNFULFILLED;

    public static final Set<RideStatus> CANCELLABLE =
            EnumSet.of(PENDING_APPROVAL, SCHEDULED, SEARCHING, OFFERED, ACCEPTED, DRIVER_ARRIVED, UNFULFILLED);

    /** States in which a vehicle is held for the ride. */
    public static final Set<RideStatus> HOLDS_VEHICLE = EnumSet.of(OFFERED, ACCEPTED, DRIVER_ARRIVED, IN_PROGRESS);
}
