package com.plantride.plant;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class PlantAdminController {

    private final PlantService plantService;

    public PlantAdminController(PlantService plantService) {
        this.plantService = plantService;
    }

    @GetMapping("/plants")
    public List<Plant> list() {
        return plantService.list();
    }

    @PostMapping("/plants")
    public Plant create(@RequestBody Plant plant) {
        return plantService.create(plant);
    }

    @PutMapping("/plants/{id}")
    public Plant update(@PathVariable Long id, @RequestBody Plant plant) {
        return plantService.update(id, plant);
    }

    @GetMapping("/system-settings")
    public Map<String, Object> settings() {
        return Map.of("multiPlantEnabled", plantService.isMultiPlantEnabled());
    }

    @PutMapping("/system-settings")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> body) {
        if (body.containsKey("multiPlantEnabled")) {
            plantService.setMultiPlantEnabled(Boolean.TRUE.equals(body.get("multiPlantEnabled")));
        }
        return settings();
    }
}
