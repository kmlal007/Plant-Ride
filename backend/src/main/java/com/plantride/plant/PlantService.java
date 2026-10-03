package com.plantride.plant;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.common.ApiException;

@Service
public class PlantService {

    public static final String MULTI_PLANT_ENABLED = "multi_plant_enabled";

    private final PlantRepository plants;
    private final SystemSettingRepository settings;

    public PlantService(PlantRepository plants, SystemSettingRepository settings) {
        this.plants = plants;
        this.settings = settings;
    }

    public Plant require(Long plantId) {
        return plants.findById(plantId).orElseThrow(() -> ApiException.notFound("Plant"));
    }

    public List<Plant> list() {
        return plants.findAll();
    }

    public boolean isMultiPlantEnabled() {
        return settings.findById(MULTI_PLANT_ENABLED)
                .map(s -> Boolean.parseBoolean(s.getSettingValue()))
                .orElse(false);
    }

    @Transactional
    public void setMultiPlantEnabled(boolean enabled) {
        if (!enabled && plants.count() > 1) {
            throw ApiException.conflict("Cannot switch to single-plant mode while more than one plant exists");
        }
        settings.save(new SystemSetting(MULTI_PLANT_ENABLED, Boolean.toString(enabled)));
    }

    @Transactional
    public Plant create(Plant plant) {
        if (plants.count() >= 1 && !isMultiPlantEnabled()) {
            throw ApiException.conflict("Multi-plant mode is disabled. Enable it in system settings first.");
        }
        plant.setId(null);
        return plants.save(plant);
    }

    @Transactional
    public Plant update(Long id, Plant changes) {
        Plant plant = require(id);
        plant.setName(changes.getName());
        plant.setTimezone(changes.getTimezone());
        plant.setCenterLat(changes.getCenterLat());
        plant.setCenterLng(changes.getCenterLng());
        plant.setSpeedLimitKmh(changes.getSpeedLimitKmh());
        plant.setDispatchRadiusKm(changes.getDispatchRadiusKm());
        plant.setOfferTimeoutSeconds(changes.getOfferTimeoutSeconds());
        plant.setSearchTimeoutMinutes(changes.getSearchTimeoutMinutes());
        plant.setScheduledDispatchLeadMinutes(changes.getScheduledDispatchLeadMinutes());
        plant.setExclusiveRideRequiresApproval(changes.isExclusiveRideRequiresApproval());
        plant.setVisitorModuleEnabled(changes.isVisitorModuleEnabled());
        plant.setActive(changes.isActive());
        return plant;
    }
}
