package com.ticketbooking.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
        @NotBlank @Size(max = 120) String name,
        @Min(1) @Max(500) int seatCount) {
}
