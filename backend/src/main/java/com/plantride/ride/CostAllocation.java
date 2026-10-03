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

/** The charge of one ride to one cost center (a shared ride will produce several rows). */
@Entity
@Table(name = "cost_allocation")
@Getter
@Setter
public class CostAllocation {

    public static final String BASIS_RIDE_FARE = "RIDE_FARE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantId;
    private Long rideRequestId;
    private Long costCenterId;
    private Long projectId;
    private BigDecimal amount;
    private String basis;
    private Instant allocatedAt;
}
