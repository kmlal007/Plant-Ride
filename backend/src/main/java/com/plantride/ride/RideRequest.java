package com.plantride.ride;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.plantride.fleet.VehicleType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ride_request")
@Getter
@Setter
public class RideRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantId;
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    private RideType rideType;

    /** Requested vehicle type; null means any. */
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    private String pickupLabel;
    private double pickupLat;
    private double pickupLng;
    private String dropLabel;
    private double dropLat;
    private double dropLng;
    private Instant scheduledAt;
    private int passengerCount = 1;
    private String purpose;
    private Long costCenterId;
    private Long projectId;

    // Optional visitor module: ride booked by a host for a visitor or delegate.
    private String visitorName;
    private String visitorPhone;
    private String gatePassRef;

    @Enumerated(EnumType.STRING)
    private RideStatus status;

    private Long approverId;
    private Long vehicleId;
    private Long driverId;
    private String otp;
    /** Drivers who declined or let the offer expire; not offered this ride again. */
    private String rejectedDriverIds;

    private Instant createdAt;
    private Instant searchStartedAt;
    private Instant offerExpiresAt;
    private Instant approvedAt;
    private Instant acceptedAt;
    private Instant arrivedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancelReason;

    private BigDecimal distanceKm;
    private Integer durationMinutes;
    /** GPS when measured from the vehicle track, ESTIMATED when the track was missing. */
    private String distanceSource;
    private BigDecimal fare;

    @Version
    private long version;

    public Set<Long> rejectedDrivers() {
        if (rejectedDriverIds == null || rejectedDriverIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rejectedDriverIds.split(",")).map(Long::valueOf).collect(Collectors.toSet());
    }

    public void addRejectedDriver(Long driverId) {
        if (driverId == null || rejectedDrivers().contains(driverId)) {
            return;
        }
        rejectedDriverIds = rejectedDriverIds == null || rejectedDriverIds.isBlank()
                ? driverId.toString() : rejectedDriverIds + "," + driverId;
    }
}
