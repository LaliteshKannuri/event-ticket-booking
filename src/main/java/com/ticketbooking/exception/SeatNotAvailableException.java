package com.ticketbooking.exception;

public class SeatNotAvailableException extends ConflictException {

    public SeatNotAvailableException(Long seatId, String label) {
        super("Seat " + label + " (id " + seatId + ") is not available");
    }
}
