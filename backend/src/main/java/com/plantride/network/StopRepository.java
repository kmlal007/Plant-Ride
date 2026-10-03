package com.plantride.network;

import java.util.List;

import com.plantride.common.PlantScopedRepository;

public interface StopRepository extends PlantScopedRepository<Stop> {

    List<Stop> findByPlantIdAndActiveTrue(Long plantId);
}
