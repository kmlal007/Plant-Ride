package com.plantride.tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.common.GeoUtils;
import com.plantride.fleet.Vehicle;
import com.plantride.fleet.VehicleRepository;
import com.plantride.plant.PlantService;

@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    /** Do not raise a new over-speed event for the same vehicle more often than this. */
    static final Duration OVER_SPEED_DEBOUNCE = Duration.ofMinutes(2);
    /** Jumps implying more than this speed between consecutive fixes are treated as GPS noise. */
    static final double MAX_PLAUSIBLE_SPEED_KMH = 150;

    public enum IngestResult { ACCEPTED, UNKNOWN_DEVICE, INVALID }

    private final VehicleRepository vehicles;
    private final VehiclePositionRepository positions;
    private final SafetyEventRepository safetyEvents;
    private final PlantService plantService;

    public TrackingService(VehicleRepository vehicles, VehiclePositionRepository positions,
                           SafetyEventRepository safetyEvents, PlantService plantService) {
        this.vehicles = vehicles;
        this.positions = positions;
        this.safetyEvents = safetyEvents;
        this.plantService = plantService;
    }

    @Transactional
    public IngestResult ingest(String deviceId, double lat, double lng, Double speedKmh, Instant fixTime) {
        if (deviceId == null || fixTime == null || Math.abs(lat) > 90 || Math.abs(lng) > 180
                || (lat == 0 && lng == 0)) {
            return IngestResult.INVALID;
        }
        Vehicle vehicle = vehicles.findByGpsDeviceId(deviceId).orElse(null);
        if (vehicle == null) {
            log.warn("Position from unregistered GPS device {}", deviceId);
            return IngestResult.UNKNOWN_DEVICE;
        }

        VehiclePosition p = new VehiclePosition();
        p.setVehicleId(vehicle.getId());
        p.setLat(lat);
        p.setLng(lng);
        p.setSpeedKmh(speedKmh);
        p.setFixTime(fixTime);
        positions.save(p);

        // Devices buffer fixes while out of coverage and replay them later: keep them in history,
        // but only move the "latest position" forward in time.
        if (vehicle.getLastFixAt() == null || fixTime.isAfter(vehicle.getLastFixAt())) {
            vehicle.setLastLat(lat);
            vehicle.setLastLng(lng);
            vehicle.setLastSpeedKmh(speedKmh);
            vehicle.setLastFixAt(fixTime);
        }

        int limit = plantService.require(vehicle.getPlantId()).getSpeedLimitKmh();
        if (speedKmh != null && speedKmh > limit && !safetyEvents.existsByVehicleIdAndEventTypeAndOccurredAtAfter(
                vehicle.getId(), SafetyEvent.OVER_SPEED, fixTime.minus(OVER_SPEED_DEBOUNCE))) {
            SafetyEvent e = new SafetyEvent();
            e.setPlantId(vehicle.getPlantId());
            e.setVehicleId(vehicle.getId());
            e.setEventType(SafetyEvent.OVER_SPEED);
            e.setLat(lat);
            e.setLng(lng);
            e.setSpeedKmh(speedKmh);
            e.setOccurredAt(fixTime);
            safetyEvents.save(e);
        }
        return IngestResult.ACCEPTED;
    }

    /**
     * Distance driven by a vehicle in a time window, from its GPS track. Implausible jumps between
     * fixes are skipped. Returns null when there are too few fixes to measure.
     */
    @Transactional(readOnly = true)
    public BigDecimal measuredDistanceKm(Long vehicleId, Instant from, Instant to) {
        List<VehiclePosition> track = positions.findByVehicleIdAndFixTimeBetweenOrderByFixTimeAsc(vehicleId, from, to);
        if (track.size() < 2) {
            return null;
        }
        double km = 0;
        for (int i = 1; i < track.size(); i++) {
            VehiclePosition a = track.get(i - 1);
            VehiclePosition b = track.get(i);
            double segment = GeoUtils.haversineKm(a.getLat(), a.getLng(), b.getLat(), b.getLng());
            double hours = Math.max(1, Duration.between(a.getFixTime(), b.getFixTime()).toSeconds()) / 3600.0;
            if (segment / hours <= MAX_PLAUSIBLE_SPEED_KMH) {
                km += segment;
            }
        }
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }
}
