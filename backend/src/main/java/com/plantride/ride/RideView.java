package com.plantride.ride;

import java.math.BigDecimal;
import java.time.Instant;

import com.plantride.fleet.Vehicle;
import com.plantride.fleet.VehicleType;
import com.plantride.user.AppUser;

/**
 * API representation of a ride, returned only to parties of the ride (requester, driver, approver) and
 * staff. The OTP is only included for the requester, who must share it with the driver.
 */
public record RideView(Long id, RideStatus status, RideType rideType, VehicleType vehicleType,
                       String pickupLabel, double pickupLat, double pickupLng,
                       String dropLabel, double dropLat, double dropLng,
                       Instant scheduledAt, int passengerCount, String purpose,
                       Long costCenterId, Long projectId, String visitorName, String visitorPhone, String gatePassRef,
                       Long requesterId, String requesterName, String requesterPhone,
                       Long vehicleId, String vehicleRegistrationNo, Double vehicleLat, Double vehicleLng,
                       Long driverId, String driverName, String driverPhone,
                       String otp, Instant offerExpiresAt, Instant createdAt, Instant arrivedAt, Instant startedAt,
                       Instant completedAt, String cancelReason,
                       BigDecimal distanceKm, Integer durationMinutes, String distanceSource, BigDecimal fare) {

    public static RideView of(RideRequest r, AppUser requester, Vehicle vehicle, AppUser driver, boolean includeOtp) {
        return new RideView(r.getId(), r.getStatus(), r.getRideType(), r.getVehicleType(),
                r.getPickupLabel(), r.getPickupLat(), r.getPickupLng(),
                r.getDropLabel(), r.getDropLat(), r.getDropLng(),
                r.getScheduledAt(), r.getPassengerCount(), r.getPurpose(),
                r.getCostCenterId(), r.getProjectId(), r.getVisitorName(), r.getVisitorPhone(), r.getGatePassRef(),
                r.getRequesterId(), requester == null ? null : requester.getName(),
                requester == null ? null : requester.getPhone(),
                r.getVehicleId(), vehicle == null ? null : vehicle.getRegistrationNo(),
                vehicle == null ? null : vehicle.getLastLat(), vehicle == null ? null : vehicle.getLastLng(),
                r.getDriverId(), driver == null ? null : driver.getName(), driver == null ? null : driver.getPhone(),
                includeOtp ? r.getOtp() : null, r.getOfferExpiresAt(), r.getCreatedAt(), r.getArrivedAt(), r.getStartedAt(),
                r.getCompletedAt(), r.getCancelReason(),
                r.getDistanceKm(), r.getDurationMinutes(), r.getDistanceSource(), r.getFare());
    }
}
