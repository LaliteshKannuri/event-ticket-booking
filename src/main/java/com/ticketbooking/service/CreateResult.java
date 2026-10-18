package com.ticketbooking.service;

import com.ticketbooking.web.dto.BookingResponse;

/** replayed is true when an Idempotency-Key matched an earlier request and that booking was returned. */
public record CreateResult(BookingResponse booking, boolean replayed) {
}
