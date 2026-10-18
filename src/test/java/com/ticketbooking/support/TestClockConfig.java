package com.ticketbooking.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfig {

    /**
     * Named differently from the production "clock" bean on purpose: Spring Boot refuses to let one
     * bean override another of the same name. @Primary makes this one win when a Clock is injected.
     */
    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    }
}
