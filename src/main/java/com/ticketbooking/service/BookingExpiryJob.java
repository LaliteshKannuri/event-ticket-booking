package com.ticketbooking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically releases seats held by bookings that were never confirmed.
 *
 * This runs on every application instance. With several instances they would all sweep; the
 * optimistic lock on Booking keeps that safe (one wins, the others retry on the next run), but
 * a real multi-instance deployment would add a distributed lock such as ShedLock.
 */
@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(
            fixedDelayString = "${booking.expiry-sweep-ms:30000}",
            initialDelayString = "${booking.expiry-sweep-initial-delay-ms:30000}")
    public void expireStaleBookings() {
        try {
            int expired = bookingService.expireStaleBookings();
            if (expired > 0) {
                log.info("Expired {} stale booking(s) and released their seats", expired);
            }
        } catch (RuntimeException e) {
            // A failed sweep (for example a concurrent cancel) is simply retried on the next run.
            log.warn("Expiry sweep failed, will retry on the next run: {}", e.toString());
        }
    }
}
