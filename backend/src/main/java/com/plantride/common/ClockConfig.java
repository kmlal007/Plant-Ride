package com.plantride.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Injected everywhere "now" is needed so that time-dependent logic is testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
