package com.plantride.user;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.common.ApiException;
import com.plantride.fleet.VendorRepository;
import com.plantride.org.CostCenterRepository;
import com.plantride.org.DepartmentRepository;
import com.plantride.security.CurrentUser;
import com.plantride.security.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public class UserAdminController {

    public record UserRequest(@NotBlank String loginId, @NotBlank String name, String phone, String email,
                              String password, @NotNull Role role, String grade, Long departmentId,
                              Long defaultCostCenterId, Long managerId, Long vendorId, Boolean active) {
    }

    private final AppUserRepository users;
    private final DepartmentRepository departments;
    private final CostCenterRepository costCenters;
    private final VendorRepository vendors;
    private final PasswordEncoder passwordEncoder;

    public UserAdminController(AppUserRepository users, DepartmentRepository departments,
                               CostCenterRepository costCenters, VendorRepository vendors,
                               PasswordEncoder passwordEncoder) {
        this.users = users;
        this.departments = departments;
        this.costCenters = costCenters;
        this.vendors = vendors;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<AppUser> list() {
        return users.findByPlantIdOrderByIdAsc(CurrentUser.get().plantId());
    }

    @PostMapping
    public AppUser create(@Valid @RequestBody UserRequest req) {
        if (req.password() == null || req.password().length() < 6) {
            throw ApiException.badRequest("password must be at least 6 characters");
        }
        AppUser user = new AppUser();
        user.setPlantId(CurrentUser.get().plantId());
        apply(user, req);
        return users.save(user);
    }

    @PutMapping("/{id}")
    public AppUser update(@PathVariable Long id, @Valid @RequestBody UserRequest req) {
        AppUser user = users.require(id, CurrentUser.get().plantId(), "User");
        apply(user, req);
        return user;
    }

    private void apply(AppUser user, UserRequest req) {
        Long plantId = user.getPlantId();
        if (req.departmentId() != null) {
            departments.require(req.departmentId(), plantId, "Department");
        }
        if (req.defaultCostCenterId() != null) {
            costCenters.require(req.defaultCostCenterId(), plantId, "Cost center");
        }
        if (req.managerId() != null) {
            if (req.managerId().equals(user.getId())) {
                throw ApiException.badRequest("A user cannot be their own manager");
            }
            users.require(req.managerId(), plantId, "Manager");
        }
        if (req.vendorId() != null) {
            vendors.require(req.vendorId(), plantId, "Vendor");
        }
        user.setLoginId(req.loginId().trim());
        user.setName(req.name());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        user.setRole(req.role());
        user.setGrade(req.grade());
        user.setDepartmentId(req.departmentId());
        user.setDefaultCostCenterId(req.defaultCostCenterId());
        user.setManagerId(req.managerId());
        user.setVendorId(req.vendorId());
        user.setActive(req.active() == null || req.active());
        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }
    }
}
