package com.ticketbooking.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.SeatStatus;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /** Ordered by id so every request touches seats in the same order, which avoids lock-order deadlocks. */
    List<Seat> findByEventIdAndIdInOrderById(Long eventId, Collection<Long> ids);

    List<Seat> findByEventIdOrderById(Long eventId);

    long countByEventId(Long eventId);

    long countByEventIdAndStatus(Long eventId, SeatStatus status);
}
