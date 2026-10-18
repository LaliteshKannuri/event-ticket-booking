package com.ticketbooking.web.dto;

import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.SeatStatus;

public record SeatResponse(Long id, String label, SeatStatus status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getLabel(), seat.getStatus());
    }
}
