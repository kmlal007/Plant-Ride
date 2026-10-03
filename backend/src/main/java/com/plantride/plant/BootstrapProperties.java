package com.plantride.plant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plantride.bootstrap")
public record BootstrapProperties(String plantCode, String plantName, String adminLogin, String adminPassword) {
}
