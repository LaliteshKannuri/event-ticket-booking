package com.ticketbooking.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketbooking.service.EventService;
import com.ticketbooking.web.dto.CreateEventRequest;
import com.ticketbooking.web.dto.EventResponse;
import com.ticketbooking.web.dto.SeatResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse created = eventService.createEvent(request.name(), request.seatCount());
        return ResponseEntity.created(URI.create("/events/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return eventService.getEvent(id);
    }

    @GetMapping("/{id}/seats")
    public List<SeatResponse> seats(@PathVariable Long id) {
        return eventService.listSeats(id);
    }
}
