package com.plantride.security;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.plantride.common.ApiException;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw ApiException.forbidden("Not authenticated");
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        Number plantId = (Number) jwt.getClaims().get("plantId");
        return new AuthUser(Long.valueOf(jwt.getSubject()), plantId.longValue(), Role.valueOf(roles.get(0)));
    }
}
