package com.plantride.fleet;

import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@DynamicUpdate
@Table(name = "vehicle")
@Getter
@Setter
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantId;
    private String registrationNo;

    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    /** Passenger seats, excluding the driver. */
    private int capacity;

    @Enumerated(EnumType.STRING)
    private OwnerType ownerType;

    private Long ownerDepartmentId;
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    private ServiceMode serviceMode;

    /** For FIXED_ROUTE vehicles: the route currently operated; enables live arrival estimates. */
    private Long routeId;

    /** Unique id the GPS device reports (IMEI / Traccar uniqueId). */
    private String gpsDeviceId;

    @Enumerated(EnumType.STRING)
    private VehicleStatus status = VehicleStatus.OFF_DUTY;

    private Long currentDriverId;
    private Double lastLat;
    private Double lastLng;
    private Double lastSpeedKmh;
    private Instant lastFixAt;
    private boolean active = true;
}
