package com.ticketbooking.service;

import java.time.Clock;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketbooking.domain.Event;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.SeatStatus;
import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.web.dto.EventResponse;
import com.ticketbooking.web.dto.SeatResponse;

@Service
public class EventService {

    static final int MAX_SEATS = 500;

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    public EventService(EventRepository eventRepository, SeatRepository seatRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.clock = clock;
    }

    @Transactional
    public EventResponse createEvent(String name, int seatCount) {
        if (seatCount < 1 || seatCount > MAX_SEATS) {
            throw new BadRequestException("seatCount must be between 1 and " + MAX_SEATS);
        }
        Event event = eventRepository.save(new Event(name.trim(), clock.instant()));
        List<Seat> seats = IntStream.rangeClosed(1, seatCount)
                .mapToObj(i -> new Seat(event, "S" + i))
                .toList();
        seatRepository.saveAll(seats);
        return new EventResponse(event.getId(), event.getName(), seatCount, seatCount);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        Event event = findEvent(eventId);
        return new EventResponse(
                event.getId(),
                event.getName(),
                seatRepository.countByEventId(eventId),
                seatRepository.countByEventIdAndStatus(eventId, SeatStatus.AVAILABLE));
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> listSeats(Long eventId) {
        findEvent(eventId);
        return seatRepository.findByEventIdOrderById(eventId).stream().map(SeatResponse::from).toList();
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }
}
