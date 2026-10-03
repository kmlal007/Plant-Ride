package com.plantride.notification;

import java.time.Clock;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.common.ApiException;
import com.plantride.security.AuthUser;
import com.plantride.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Apps register their push token after login and unregister it on logout. */
@RestController
@RequestMapping("/api/me/devices")
public class DeviceController {

    public record RegisterRequest(@NotBlank @Size(max = 512) String token, @NotBlank String platform,
                                  @NotBlank String app) {
    }

    public record UnregisterRequest(@NotBlank String token) {
    }

    private static final Set<String> PLATFORMS = Set.of("android", "ios");
    private static final Set<String> APPS = Set.of("user", "driver");

    private final DeviceTokenRepository tokens;
    private final Clock clock;

    public DeviceController(DeviceTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @PostMapping
    @Transactional
    public void register(@Valid @RequestBody RegisterRequest req) {
        if (!PLATFORMS.contains(req.platform()) || !APPS.contains(req.app())) {
            throw ApiException.badRequest("platform must be android|ios and app must be user|driver");
        }
        AuthUser me = CurrentUser.get();
        // A token moves to whoever signed in last on that device.
        DeviceToken t = tokens.findByToken(req.token()).orElseGet(() -> {
            DeviceToken fresh = new DeviceToken();
            fresh.setToken(req.token());
            fresh.setCreatedAt(clock.instant());
            return fresh;
        });
        t.setUserId(me.userId());
        t.setPlatform(req.platform());
        t.setApp(req.app());
        t.setLastSeenAt(clock.instant());
        tokens.save(t);
    }

    @PostMapping("/unregister")
    public void unregister(@Valid @RequestBody UnregisterRequest req) {
        tokens.deleteByUserIdAndToken(CurrentUser.get().userId(), req.token());
    }
}
