package com.plantride.tracking;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.plantride.common.ApiException;

/**
 * Ingestion endpoints for the GPS gateway. Devices talk to Traccar (which decodes 200+ device
 * protocols); Traccar forwards each position here. A generic batch format is also accepted.
 */
@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private static final double KNOTS_TO_KMH = 1.852;

    public record PositionIn(String deviceId, double lat, double lng, Double speedKmh, Instant fixTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraccarPosition(double latitude, double longitude, Double speed, Instant fixTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraccarDevice(String uniqueId) {
    }

    /** Body posted by Traccar with {@code forward.type=json}; speed is in knots. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraccarForward(TraccarPosition position, TraccarDevice device) {
    }

    private final TrackingService trackingService;
    private final String apiKey;

    public TrackingController(TrackingService trackingService, @Value("${plantride.tracking.api-key}") String apiKey) {
        this.trackingService = trackingService;
        this.apiKey = apiKey;
    }

    @PostMapping("/positions")
    public Map<String, Integer> positions(@RequestHeader(value = "X-Api-Key", required = false) String key,
                                          @RequestBody List<PositionIn> batch) {
        checkKey(key);
        int accepted = 0;
        for (PositionIn p : batch) {
            if (trackingService.ingest(p.deviceId(), p.lat(), p.lng(), p.speedKmh(), p.fixTime())
                    == TrackingService.IngestResult.ACCEPTED) {
                accepted++;
            }
        }
        return Map.of("received", batch.size(), "accepted", accepted);
    }

    @PostMapping("/traccar")
    public ResponseEntity<Void> traccar(@RequestHeader(value = "X-Api-Key", required = false) String key,
                                        @RequestBody TraccarForward body) {
        checkKey(key);
        if (body.position() == null || body.device() == null) {
            throw ApiException.badRequest("position and device are required");
        }
        Double kmh = body.position().speed() == null ? null : body.position().speed() * KNOTS_TO_KMH;
        trackingService.ingest(body.device().uniqueId(), body.position().latitude(), body.position().longitude(),
                kmh, body.position().fixTime());
        // Always 200 for well-formed input so Traccar does not retry unknown devices forever.
        return ResponseEntity.ok().build();
    }

    private void checkKey(String key) {
        if (key == null || !java.security.MessageDigest.isEqual(key.getBytes(), apiKey.getBytes())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid tracking API key");
        }
    }
}
