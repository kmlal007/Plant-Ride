package com.plantride.security;

/** Identity of the caller, resolved from the JWT. */
public record AuthUser(Long userId, Long plantId, Role role) {
}
