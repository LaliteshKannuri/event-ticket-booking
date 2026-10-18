package com.ticketbooking.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class BookingExpiryJobTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingExpiryJob job;

    @Test
    void sweepCallsTheService() {
        when(bookingService.expireStaleBookings()).thenReturn(3);

        job.expireStaleBookings();

        verify(bookingService).expireStaleBookings();
    }

    @Test
    void failedSweepDoesNotThrow_soTheScheduleKeepsRunning() {
        when(bookingService.expireStaleBookings())
                .thenThrow(new OptimisticLockingFailureException("booking was cancelled concurrently"));

        assertDoesNotThrow(() -> job.expireStaleBookings());
    }
}
