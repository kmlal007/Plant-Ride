package com.plantride.demo;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pilot showcase settings. Never enable in production.
 *
 * @param seed            create the "Demo Steel Works" pilot plant with history on an empty demo plant
 * @param simulator       move vehicles with SIM- GPS ids and run bot drivers
 * @param botDriverLogins drivers the simulator operates automatically (all others are driven by a human)
 * @param speedKmh        simulated driving speed of on-demand vehicles
 */
@ConfigurationProperties(prefix = "plantride.demo")
public record DemoProperties(boolean seed, boolean simulator, List<String> botDriverLogins, double speedKmh) {
}
