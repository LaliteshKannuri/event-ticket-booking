package com.ticketbooking.exception;

public class BookingExpiredException extends ConflictException {

    public BookingExpiredException(Long bookingId) {
        super("The hold for booking " + bookingId + " has expired");
    }
}
