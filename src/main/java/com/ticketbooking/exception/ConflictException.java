package com.ticketbooking.exception;

/** Base class for "the request is valid but clashes with the current state". Maps to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
