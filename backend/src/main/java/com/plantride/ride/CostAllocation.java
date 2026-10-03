package com.plantride.ride;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One charge (or credit, when negative) to a cost center: either for a ride, or for a period's share of
 * fixed shuttle costs. Rows are append-only so reports reconcile with what Finance received.
 */
@Entity
@Table(name = "cost_allocation")
@Getter
@Setter
public class CostAllocation {

    public static final String BASIS_RIDE_FARE = "RIDE_FARE";
    public static final String BASIS_OWN_VEHICLE = "OWN_VEHICLE";
    public static final String BASIS_LENT_VEHICLE_USE = "LENT_VEHICLE_USE";
    public static final String BASIS_LENT_VEHICLE_CREDIT = "LENT_VEHICLE_CREDIT";
    public static final String BASIS_SHUTTLE_CENTRAL = "SHUTTLE_CENTRAL";
    public static final String BASIS_SHUTTLE_HEADCOUNT = "SHUTTLE_HEADCOUNT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantId;
    /** Set for ride charges; null for periodic shuttle allocations. */
    private Long rideRequestId;
    /** yyyy-MM for periodic allocations. */
    private String period;
    private Long costCenterId;
    private Long projectId;
    private BigDecimal amount;
    private String basis;
    private Instant allocatedAt;
}
