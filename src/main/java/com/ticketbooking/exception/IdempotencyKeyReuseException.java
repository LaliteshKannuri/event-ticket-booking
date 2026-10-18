package com.ticketbooking.exception;

/** The same Idempotency-Key was sent with a different request. Maps to HTTP 422. */
public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request");
    }
}
