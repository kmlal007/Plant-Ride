package com.plantride.fleet;

import java.math.BigDecimal;

import com.plantride.ride.RideType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Internal charge rate used to cost a ride to the booking cost center. */
@Entity
@Table(name = "rate_card")
@Getter
@Setter
public class RateCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantId;

    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    private RideType rideType;

    private BigDecimal baseFare;
    private BigDecimal perKm;
    private BigDecimal perMinute;
    private BigDecimal minimumFare;
}
