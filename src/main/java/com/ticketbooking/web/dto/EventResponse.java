package com.ticketbooking.web.dto;

public record EventResponse(Long id, String name, long totalSeats, long availableSeats) {
}
