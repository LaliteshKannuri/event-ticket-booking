package com.ticketbooking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    /** Used by the expiry job; served by the (status, expires_at) index. */
    List<Booking> findByStatusAndExpiresAtLessThanEqual(BookingStatus status, Instant cutoff);
}
