package com.ticketbooking.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.CreateResult;
import com.ticketbooking.web.dto.BookingResponse;
import com.ticketbooking.web.dto.CreateBookingRequest;

import jakarta.validation.Valid;

@RestController
public class BookingController {

    private static final int MAX_KEY_LENGTH = 100;

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * 201 for a new booking. If the Idempotency-Key matches an earlier request, the original
     * booking is returned with 200 and nothing new is created.
     */
    @PostMapping("/events/{eventId}/bookings")
    public ResponseEntity<BookingResponse> create(
            @PathVariable Long eventId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {

        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH)) {
            throw new BadRequestException(
                    "Idempotency-Key must be 1 to " + MAX_KEY_LENGTH + " characters");
        }

        CreateResult result = bookingService.createBooking(eventId, request.seatIds(), idempotencyKey);
        BookingResponse booking = result.booking();
        if (result.replayed()) {
            return ResponseEntity.ok(booking);
        }
        return ResponseEntity.created(URI.create("/bookings/" + booking.id())).body(booking);
    }

    @GetMapping("/bookings/{id}")
    public BookingResponse get(@PathVariable Long id) {
        return bookingService.getBooking(id);
    }

    @PostMapping("/bookings/{id}/confirm")
    public BookingResponse confirm(@PathVariable Long id) {
        return bookingService.confirm(id);
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id) {
        return bookingService.cancel(id);
    }
}
