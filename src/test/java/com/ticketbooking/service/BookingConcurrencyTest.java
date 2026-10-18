package com.ticketbooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.SeatStatus;
import com.ticketbooking.exception.ConflictException;
import com.ticketbooking.support.IntegrationTestBase;

/**
 * Many threads try to book at the same moment. The service must never hand the same seat to two
 * bookings. Losers are allowed to fail in only three ways: the seat was already taken, an optimistic
 * lock failed, or a database constraint stopped the write.
 */
class BookingConcurrencyTest extends IntegrationTestBase {

    private static final int THREADS = 50;

    @Autowired
    private EventService eventService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void exactlyOneOfFiftyRequestsGetsTheSameSeat() throws Exception {
        Long eventId = eventService.createEvent("Concert", 1).id();
        List<Long> seatIds = seatRepository.findByEventIdOrderById(eventId).stream().map(Seat::getId).toList();

        RaceResult result = race(eventId, seatIds);

        assertEquals(1, result.successes, "exactly one request should win the seat");
        assertEquals(THREADS - 1, result.failures.size());
        assertTrue(result.unexpectedFailures().isEmpty(), "unexpected failures: " + result.unexpectedFailures());
        assertEquals(SeatStatus.HELD, seatRepository.findById(seatIds.get(0)).orElseThrow().getStatus());
        assertEquals(1L, bookingRepository.count());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from booking_seats", Integer.class));
    }

    @Test
    void fiftyRequestsOverFiveSeatsBookEachSeatExactlyOnce() throws Exception {
        Long eventId = eventService.createEvent("Concert", 5).id();
        List<Long> seatIds = seatRepository.findByEventIdOrderById(eventId).stream().map(Seat::getId).toList();

        RaceResult result = race(eventId, seatIds);

        assertEquals(5, result.successes, "one booking per seat, 5 in total");
        assertTrue(result.unexpectedFailures().isEmpty(), "unexpected failures: " + result.unexpectedFailures());
        assertEquals(5L, bookingRepository.count());
        assertEquals(5, jdbcTemplate.queryForObject("select count(*) from booking_seats", Integer.class));
        assertEquals(5, jdbcTemplate.queryForObject(
                "select count(distinct seat_id) from booking_seats", Integer.class),
                "no seat may belong to two bookings");
    }

    /** Starts THREADS threads together; thread i asks for seat i % seats.size(). */
    private RaceResult race(Long eventId, List<Long> seatIds) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Future<Void>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < THREADS; i++) {
                Long seatId = seatIds.get(i % seatIds.size());
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        bookingService.createBooking(eventId, List.of(seatId), null);
                        successes.incrementAndGet();
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "threads did not start in time");
            go.countDown();
            for (Future<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return new RaceResult(successes.get(), failures);
    }

    private record RaceResult(int successes, List<Throwable> failures) {

        List<Throwable> unexpectedFailures() {
            return failures.stream()
                    .filter(t -> !(t instanceof ConflictException
                            || t instanceof ConcurrencyFailureException
                            || t instanceof DataIntegrityViolationException))
                    .toList();
        }
    }
}
