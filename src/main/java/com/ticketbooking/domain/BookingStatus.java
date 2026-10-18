package com.ticketbooking.domain;

/**
 * The booking state machine. A booking starts as PENDING and can move to exactly one of the
 * other three states. Those three are final.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED || target == EXPIRED;
            case CONFIRMED, CANCELLED, EXPIRED -> false;
        };
    }
}
