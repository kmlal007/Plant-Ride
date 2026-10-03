package com.plantride.org;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.security.CurrentUser;

/** Master data for departments, cost centers and projects of the admin's current plant. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public class OrgAdminController {

    private final DepartmentRepository departments;
    private final CostCenterRepository costCenters;
    private final ProjectRepository projects;

    public OrgAdminController(DepartmentRepository departments, CostCenterRepository costCenters,
                              ProjectRepository projects) {
        this.departments = departments;
        this.costCenters = costCenters;
        this.projects = projects;
    }

    @GetMapping("/departments")
    public List<Department> listDepartments() {
        return departments.findByPlantIdOrderByIdAsc(CurrentUser.get().plantId());
    }

    @PostMapping("/departments")
    public Department createDepartment(@RequestBody Department body) {
        body.setId(null);
        body.setPlantId(CurrentUser.get().plantId());
        return departments.save(body);
    }

    @PutMapping("/departments/{id}")
    public Department updateDepartment(@PathVariable Long id, @RequestBody Department body) {
        Department d = departments.require(id, CurrentUser.get().plantId(), "Department");
        d.setCode(body.getCode());
        d.setName(body.getName());
        d.setLat(body.getLat());
        d.setLng(body.getLng());
        d.setActive(body.isActive());
        return d;
    }

    @GetMapping("/cost-centers")
    public List<CostCenter> listCostCenters() {
        return costCenters.findByPlantIdOrderByIdAsc(CurrentUser.get().plantId());
    }

    @PostMapping("/cost-centers")
    public CostCenter createCostCenter(@RequestBody CostCenter body) {
        Long plantId = CurrentUser.get().plantId();
        validateDepartment(body.getDepartmentId(), plantId);
        body.setId(null);
        body.setPlantId(plantId);
        return costCenters.save(body);
    }

    @PutMapping("/cost-centers/{id}")
    public CostCenter updateCostCenter(@PathVariable Long id, @RequestBody CostCenter body) {
        Long plantId = CurrentUser.get().plantId();
        CostCenter cc = costCenters.require(id, plantId, "Cost center");
        validateDepartment(body.getDepartmentId(), plantId);
        cc.setCode(body.getCode());
        cc.setName(body.getName());
        cc.setDepartmentId(body.getDepartmentId());
        cc.setMonthlyBudget(body.getMonthlyBudget());
        cc.setActive(body.isActive());
        return cc;
    }

    @GetMapping("/projects")
    public List<Project> listProjects() {
        return projects.findByPlantIdOrderByIdAsc(CurrentUser.get().plantId());
    }

    @PostMapping("/projects")
    public Project createProject(@RequestBody Project body) {
        Long plantId = CurrentUser.get().plantId();
        costCenters.require(body.getCostCenterId(), plantId, "Cost center");
        body.setId(null);
        body.setPlantId(plantId);
        return projects.save(body);
    }

    @PutMapping("/projects/{id}")
    public Project updateProject(@PathVariable Long id, @RequestBody Project body) {
        Long plantId = CurrentUser.get().plantId();
        Project p = projects.require(id, plantId, "Project");
        costCenters.require(body.getCostCenterId(), plantId, "Cost center");
        p.setCode(body.getCode());
        p.setName(body.getName());
        p.setCostCenterId(body.getCostCenterId());
        p.setActive(body.isActive());
        return p;
    }

    private void validateDepartment(Long departmentId, Long plantId) {
        if (departmentId != null) {
            departments.require(departmentId, plantId, "Department");
        }
    }
}
