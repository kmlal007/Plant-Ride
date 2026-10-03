package com.plantride.tracking;

import java.time.Instant;
import java.util.List;

import com.plantride.common.PlantScopedRepository;

public interface SafetyEventRepository extends PlantScopedRepository<SafetyEvent> {

    boolean existsByVehicleIdAndEventTypeAndOccurredAtAfter(Long vehicleId, String eventType, Instant after);

    List<SafetyEvent> findTop100ByPlantIdOrderByOccurredAtDesc(Long plantId);
}
