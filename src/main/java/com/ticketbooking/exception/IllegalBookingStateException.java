package com.ticketbooking.exception;

import com.ticketbooking.domain.BookingStatus;

public class IllegalBookingStateException extends ConflictException {

    public IllegalBookingStateException(Long bookingId, BookingStatus from, BookingStatus to) {
        super("Booking " + bookingId + " cannot move from " + from + " to " + to);
    }
}
