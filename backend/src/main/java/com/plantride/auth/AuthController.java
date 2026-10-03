package com.plantride.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.common.ApiException;
import com.plantride.plant.PlantService;
import com.plantride.security.AuthUser;
import com.plantride.security.CurrentUser;
import com.plantride.security.JwtService;
import com.plantride.security.Role;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Username/password login issuing a JWT. Corporate SSO (OIDC) can replace this for employees
 * later; drivers and contract staff will still need this path.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {
    }

    public record LoginResponse(String token, Long userId, String name, Role role, Long plantId) {
    }

    public record SwitchPlantRequest(@NotNull Long plantId) {
    }

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PlantService plantService;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                          PlantService plantService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.plantService = plantService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByLoginId(req.loginId().trim())
                .filter(AppUser::isActive)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid login or password"));
        String token = jwtService.issue(user.getId(), user.getPlantId(), user.getRole());
        return new LoginResponse(token, user.getId(), user.getName(), user.getRole(), user.getPlantId());
    }

    /** Admins of a multi-plant deployment switch the plant they are configuring. */
    @PostMapping("/switch-plant")
    public LoginResponse switchPlant(@Valid @RequestBody SwitchPlantRequest req) {
        AuthUser me = CurrentUser.get();
        if (me.role() != Role.ADMIN) {
            throw ApiException.forbidden("Only admins can switch plant");
        }
        plantService.require(req.plantId());
        AppUser user = users.findById(me.userId()).orElseThrow(() -> ApiException.notFound("User"));
        String token = jwtService.issue(user.getId(), req.plantId(), user.getRole());
        return new LoginResponse(token, user.getId(), user.getName(), user.getRole(), req.plantId());
    }
}
