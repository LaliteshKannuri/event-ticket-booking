package com.ticketbooking.support;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;

@IntegrationTest
public abstract class IntegrationTestBase {

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    protected SeatRepository seatRepository;

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected MutableClock clock;

    /** Start every test from an empty database and the same point in time. */
    @BeforeEach
    protected void resetState() {
        bookingRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();
        clock.set(Instant.parse("2026-01-01T10:00:00Z"));
    }
}
