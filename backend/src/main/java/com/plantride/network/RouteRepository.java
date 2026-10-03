package com.plantride.network;

import java.util.List;

import com.plantride.common.PlantScopedRepository;

public interface RouteRepository extends PlantScopedRepository<Route> {

    List<Route> findByPlantIdAndActiveTrue(Long plantId);
}
