package com.ticketbooking.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.exception.IdempotencyKeyReuseException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.exception.SeatNotAvailableException;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.CreateResult;
import com.ticketbooking.web.dto.BookingResponse;

/** Tests the HTTP layer only: the service is a Mockito mock, so no database is involved. */
@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    private static BookingResponse booking(long id) {
        return new BookingResponse(id, 1L, BookingStatus.PENDING,
                Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T10:05:00Z"),
                1, List.of(5L));
    }

    @Test
    void newBookingReturns201WithLocation() throws Exception {
        given(bookingService.createBooking(eq(1L), eq(List.of(5L)), isNull()))
                .willReturn(new CreateResult(booking(42), false));

        mockMvc.perform(post("/events/1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\":[5]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/bookings/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void replayedRequestReturns200AndPassesTheKeyToTheService() throws Exception {
        given(bookingService.createBooking(1L, List.of(5L), "key-1"))
                .willReturn(new CreateResult(booking(42), true));

        mockMvc.perform(post("/events/1/bookings")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\":[5]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));

        verify(bookingService).createBooking(1L, List.of(5L), "key-1");
    }

    @Test
    void takenSeatReturns409() throws Exception {
        given(bookingService.createBooking(eq(1L), eq(List.of(5L)), isNull()))
                .willThrow(new SeatNotAvailableException(5L, "S5"));

        mockMvc.perform(post("/events/1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\":[5]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Seat S5 (id 5) is not available"));
    }

    @Test
    void reusedKeyWithDifferentRequestReturns422() throws Exception {
        given(bookingService.createBooking(1L, List.of(6L), "key-1"))
                .willThrow(new IdempotencyKeyReuseException("key-1"));

        mockMvc.perform(post("/events/1/bookings")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\":[6]}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emptySeatListReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/events/1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.seatIds").exists());
    }

    @Test
    void blankIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/events/1/bookings")
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\":[5]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownBookingReturns404() throws Exception {
        given(bookingService.getBooking(99L)).willThrow(new ResourceNotFoundException("Booking 99 not found"));

        mockMvc.perform(get("/bookings/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Booking 99 not found"));
    }
}
