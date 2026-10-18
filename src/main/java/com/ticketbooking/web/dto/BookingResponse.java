package com.ticketbooking.web.dto;

import java.time.Instant;
import java.util.List;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Seat;

/**
 * seatIds lists the seats the booking currently holds, so it is empty once a booking is cancelled
 * or has expired. seatCount keeps the original size.
 */
public record BookingResponse(
        Long id,
        Long eventId,
        BookingStatus status,
        Instant createdAt,
        Instant expiresAt,
        int seatCount,
        List<Long> seatIds) {

    /** Call inside a transaction: it reads the lazily loaded seats. */
    public static BookingResponse from(Booking booking) {
        List<Long> seatIds = booking.getSeats().stream().map(Seat::getId).sorted().toList();
        return new BookingResponse(
                booking.getId(),
                booking.getEvent().getId(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getExpiresAt(),
                booking.getSeatCount(),
                seatIds);
    }
}
