package com.ticketbooking.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(
        @NotEmpty @Size(max = 10) List<@NotNull Long> seatIds) {
}
