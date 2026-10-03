package com.plantride.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plantride.jwt")
public record JwtProperties(String secret, long ttlHours) {
}
