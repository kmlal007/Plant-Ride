package com.plantride.user;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.org.CostCenter;
import com.plantride.org.CostCenterRepository;
import com.plantride.org.Department;
import com.plantride.org.DepartmentRepository;
import com.plantride.org.Project;
import com.plantride.org.ProjectRepository;
import com.plantride.plant.Plant;
import com.plantride.plant.PlantService;
import com.plantride.security.AuthUser;
import com.plantride.security.CurrentUser;

/** Profile and the lookup data the mobile apps need to build booking screens. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record PlantInfo(Long id, String name, Double centerLat, Double centerLng, boolean visitorModuleEnabled,
                            boolean exclusiveRideRequiresApproval) {
    }

    private final AppUserRepository users;
    private final PlantService plantService;
    private final DepartmentRepository departments;
    private final CostCenterRepository costCenters;
    private final ProjectRepository projects;

    public MeController(AppUserRepository users, PlantService plantService, DepartmentRepository departments,
                        CostCenterRepository costCenters, ProjectRepository projects) {
        this.users = users;
        this.plantService = plantService;
        this.departments = departments;
        this.costCenters = costCenters;
        this.projects = projects;
    }

    @GetMapping
    public Map<String, Object> me() {
        AuthUser me = CurrentUser.get();
        AppUser user = users.findById(me.userId()).orElseThrow();
        Plant plant = plantService.require(me.plantId());
        return Map.of("user", user, "plant", new PlantInfo(plant.getId(), plant.getName(), plant.getCenterLat(),
                plant.getCenterLng(), plant.isVisitorModuleEnabled(), plant.isExclusiveRideRequiresApproval()));
    }

    /** Departments double as ride destinations ("take me to Blast Furnace 2"). */
    @GetMapping("/lookups")
    public Map<String, Object> lookups() {
        Long plantId = CurrentUser.get().plantId();
        List<Department> depts = departments.findByPlantIdOrderByIdAsc(plantId).stream()
                .filter(Department::isActive).toList();
        List<CostCenter> ccs = costCenters.findByPlantIdOrderByIdAsc(plantId).stream()
                .filter(CostCenter::isActive).toList();
        List<Project> projs = projects.findByPlantIdOrderByIdAsc(plantId).stream()
                .filter(Project::isActive).toList();
        return Map.of("departments", depts, "costCenters", ccs, "projects", projs);
    }
}
