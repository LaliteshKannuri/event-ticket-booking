package com.ticketbooking.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BookingStatusTest {

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "CANCELLED", "EXPIRED"})
    void pendingCanMoveToEachFinalState(BookingStatus target) {
        assertTrue(BookingStatus.PENDING.canTransitionTo(target));
    }

    @Test
    void pendingCannotMoveToItself() {
        assertFalse(BookingStatus.PENDING.canTransitionTo(BookingStatus.PENDING));
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "CANCELLED", "EXPIRED"})
    void finalStatesCannotMoveAnywhere(BookingStatus from) {
        for (BookingStatus target : BookingStatus.values()) {
            assertFalse(from.canTransitionTo(target), from + " -> " + target + " should be rejected");
        }
    }
}
