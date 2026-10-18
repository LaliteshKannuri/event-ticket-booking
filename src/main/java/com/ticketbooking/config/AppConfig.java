package com.ticketbooking.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * All time-based logic (hold expiry) reads the time from this Clock instead of calling
     * Instant.now(), so tests can replace it with a clock they control.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
