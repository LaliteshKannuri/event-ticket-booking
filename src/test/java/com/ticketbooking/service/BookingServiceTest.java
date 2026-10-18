package com.ticketbooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.SeatStatus;
import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.exception.BookingExpiredException;
import com.ticketbooking.exception.IdempotencyKeyReuseException;
import com.ticketbooking.exception.IllegalBookingStateException;
import com.ticketbooking.exception.SeatNotAvailableException;
import com.ticketbooking.support.IntegrationTestBase;
import com.ticketbooking.web.dto.BookingResponse;

/** Business rules, tested against a real (in-memory) database and a clock the test controls. */
class BookingServiceTest extends IntegrationTestBase {

    @Autowired
    private EventService eventService;

    @Autowired
    private BookingService bookingService;

    private Long eventId;

    private List<Long> createEventWithSeats(int count) {
        eventId = eventService.createEvent("Concert", count).id();
        return seatRepository.findByEventIdOrderById(eventId).stream().map(Seat::getId).toList();
    }

    private SeatStatus seatStatus(Long seatId) {
        return seatRepository.findById(seatId).orElseThrow().getStatus();
    }

    @Test
    void bookingHoldsSeatsForFiveMinutes() {
        List<Long> seats = createEventWithSeats(3);

        BookingResponse booking = bookingService.createBooking(eventId, List.of(seats.get(0), seats.get(1)), null).booking();

        assertEquals(BookingStatus.PENDING, booking.status());
        assertEquals(Instant.parse("2026-01-01T10:05:00Z"), booking.expiresAt());
        assertEquals(List.of(seats.get(0), seats.get(1)), booking.seatIds());
        assertEquals(SeatStatus.HELD, seatStatus(seats.get(0)));
        assertEquals(SeatStatus.HELD, seatStatus(seats.get(1)));
        assertEquals(SeatStatus.AVAILABLE, seatStatus(seats.get(2)));
    }

    @Test
    void bookingIsAllOrNothing_whenOneSeatIsTaken() {
        List<Long> seats = createEventWithSeats(2);
        bookingService.createBooking(eventId, List.of(seats.get(0)), null);

        assertThrows(SeatNotAvailableException.class,
                () -> bookingService.createBooking(eventId, List.of(seats.get(0), seats.get(1)), null));

        // The free seat must not stay held by the failed attempt.
        assertEquals(SeatStatus.AVAILABLE, seatStatus(seats.get(1)));
        assertEquals(1, bookingRepository.count());
    }

    @Test
    void confirmMarksSeatsBooked() {
        List<Long> seats = createEventWithSeats(1);
        Long bookingId = bookingService.createBooking(eventId, List.of(seats.get(0)), null).booking().id();

        BookingResponse confirmed = bookingService.confirm(bookingId);

        assertEquals(BookingStatus.CONFIRMED, confirmed.status());
        assertEquals(SeatStatus.BOOKED, seatStatus(seats.get(0)));
    }

    @Test
    void confirmingTwiceIsRejected() {
        List<Long> seats = createEventWithSeats(1);
        Long bookingId = bookingService.createBooking(eventId, List.of(seats.get(0)), null).booking().id();
        bookingService.confirm(bookingId);

        assertThrows(IllegalBookingStateException.class, () -> bookingService.confirm(bookingId));
    }

    @Test
    void confirmAfterTheHoldHasRunOutIsRejected() {
        List<Long> seats = createEventWithSeats(1);
        Long bookingId = bookingService.createBooking(eventId, List.of(seats.get(0)), null).booking().id();

        clock.advance(Duration.ofMinutes(5));

        assertThrows(BookingExpiredException.class, () -> bookingService.confirm(bookingId));
        assertEquals(BookingStatus.PENDING, bookingService.getBooking(bookingId).status());
    }

    @Test
    void cancelReleasesSeatsSoTheyCanBeBookedAgain() {
        List<Long> seats = createEventWithSeats(1);
        Long bookingId = bookingService.createBooking(eventId, List.of(seats.get(0)), null).booking().id();

        BookingResponse cancelled = bookingService.cancel(bookingId);

        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertTrue(cancelled.seatIds().isEmpty());
        assertEquals(SeatStatus.AVAILABLE, seatStatus(seats.get(0)));
        // Booking the same seat again works.
        bookingService.createBooking(eventId, List.of(seats.get(0)), null);
        assertEquals(SeatStatus.HELD, seatStatus(seats.get(0)));
    }

    @Test
    void expiryJobReleasesOnlyHoldsThatRanOut() {
        List<Long> seats = createEventWithSeats(2);
        Long first = bookingService.createBooking(eventId, List.of(seats.get(0)), null).booking().id();
        clock.advance(Duration.ofMinutes(3));
        Long second = bookingService.createBooking(eventId, List.of(seats.get(1)), null).booking().id();
        clock.advance(Duration.ofMinutes(3)); // first is 6 minutes old, second is 3

        int expired = bookingService.expireStaleBookings();

        assertEquals(1, expired);
        assertEquals(BookingStatus.EXPIRED, bookingService.getBooking(first).status());
        assertEquals(SeatStatus.AVAILABLE, seatStatus(seats.get(0)));
        assertEquals(BookingStatus.PENDING, bookingService.getBooking(second).status());
        assertEquals(SeatStatus.HELD, seatStatus(seats.get(1)));
    }

    @Test
    void retryWithTheSameKeyReturnsTheOriginalBooking() {
        List<Long> seats = createEventWithSeats(1);

        CreateResult first = bookingService.createBooking(eventId, List.of(seats.get(0)), "key-1");
        CreateResult retry = bookingService.createBooking(eventId, List.of(seats.get(0)), "key-1");

        assertFalse(first.replayed());
        assertTrue(retry.replayed());
        assertEquals(first.booking().id(), retry.booking().id());
        assertEquals(1, bookingRepository.count());
    }

    @Test
    void sameKeyWithADifferentRequestIsRejected() {
        List<Long> seats = createEventWithSeats(2);
        bookingService.createBooking(eventId, List.of(seats.get(0)), "key-1");

        assertThrows(IdempotencyKeyReuseException.class,
                () -> bookingService.createBooking(eventId, List.of(seats.get(1)), "key-1"));
        assertEquals(SeatStatus.AVAILABLE, seatStatus(seats.get(1)));
    }

    @Test
    void seatFromAnotherEventIsRejected() {
        createEventWithSeats(1);
        Long otherEventId = eventService.createEvent("Other show", 1).id();
        Long otherSeat = seatRepository.findByEventIdOrderById(otherEventId).get(0).getId();

        assertThrows(BadRequestException.class,
                () -> bookingService.createBooking(eventId, List.of(otherSeat), null));
    }
}
