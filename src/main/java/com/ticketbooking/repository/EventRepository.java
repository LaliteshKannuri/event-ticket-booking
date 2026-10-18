package com.ticketbooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbooking.domain.Event;

public interface EventRepository extends JpaRepository<Event, Long> {
}
