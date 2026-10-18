package com.ticketbooking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Event;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.exception.IdempotencyKeyReuseException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.web.dto.BookingResponse;

/**
 * Every public method runs in one database transaction, so a booking either happens completely
 * (all seats held and the booking row written) or not at all.
 *
 * Note that optimistic-lock failures are raised when the transaction commits, that is, after the
 * method body has returned and inside Spring's transaction proxy. Callers therefore see them as a
 * Spring ConcurrencyFailureException, which the web layer turns into a 409.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final Duration holdDuration;

    public BookingService(EventRepository eventRepository,
                          SeatRepository seatRepository,
                          BookingRepository bookingRepository,
                          Clock clock,
                          @Value("${booking.hold-minutes:5}") long holdMinutes) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.holdDuration = Duration.ofMinutes(holdMinutes);
    }

    @Transactional
    public CreateResult createBooking(Long eventId, List<Long> seatIds, String idempotencyKey) {
        // Sorted and de-duplicated: gives a stable fingerprint and a stable lock order.
        List<Long> requestedIds = seatIds.stream().distinct().sorted().toList();
        String requestHash = fingerprint(eventId, requestedIds);

        if (idempotencyKey != null) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Booking booking = existing.get();
                if (!requestHash.equals(booking.getRequestHash())) {
                    throw new IdempotencyKeyReuseException(idempotencyKey);
                }
                log.info("Idempotent replay of booking {} for key {}", booking.getId(), idempotencyKey);
                return new CreateResult(BookingResponse.from(booking), true);
            }
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));

        List<Seat> seats = seatRepository.findByEventIdAndIdInOrderById(eventId, requestedIds);
        if (seats.size() != requestedIds.size()) {
            throw new BadRequestException("One or more seats do not belong to event " + eventId);
        }

        // Throws SeatNotAvailableException if any seat is taken, which rolls the whole transaction back.
        seats.forEach(Seat::hold);

        Instant now = clock.instant();
        Booking booking = new Booking(event, now, now.plus(holdDuration), idempotencyKey, requestHash, seats);
        bookingRepository.save(booking);
        return new CreateResult(BookingResponse.from(booking), false);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long bookingId) {
        return BookingResponse.from(find(bookingId));
    }

    @Transactional
    public BookingResponse confirm(Long bookingId) {
        Booking booking = find(bookingId);
        booking.confirm(clock.instant());
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse cancel(Long bookingId) {
        Booking booking = find(bookingId);
        booking.cancel();
        return BookingResponse.from(booking);
    }

    /** Expires every PENDING booking whose hold has run out and frees its seats. Returns how many. */
    @Transactional
    public int expireStaleBookings() {
        List<Booking> stale = bookingRepository
                .findByStatusAndExpiresAtLessThanEqual(BookingStatus.PENDING, clock.instant());
        stale.forEach(Booking::expire);
        return stale.size();
    }

    private Booking find(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
    }

    private static String fingerprint(Long eventId, List<Long> sortedSeatIds) {
        String canonical = eventId + ":" + sortedSeatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
