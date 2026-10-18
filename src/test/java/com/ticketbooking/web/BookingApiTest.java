package com.ticketbooking.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.support.IntegrationTestBase;

/** The whole stack over HTTP with MockMvc: controller, validation, service, JPA and the in-memory database. */
class BookingApiTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingService bookingService;

    @Test
    void bookConfirmAndRetryOverHttp() throws Exception {
        long eventId = createEvent(3);
        List<Long> seatIds = seatIds(eventId);

        MvcResult created = book(eventId, seatIds.get(0), "key-A")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long bookingId = idOf(created);

        // A retry with the same key returns the same booking instead of creating a second one.
        book(eventId, seatIds.get(0), "key-A")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId));

        // Another client asking for the same seat is refused.
        book(eventId, seatIds.get(0), null).andExpect(status().isConflict());

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(3))
                .andExpect(jsonPath("$.availableSeats").value(2));
    }

    @Test
    void cancelFreesTheSeat() throws Exception {
        long eventId = createEvent(1);
        long seatId = seatIds(eventId).get(0);
        long bookingId = idOf(book(eventId, seatId, null).andExpect(status().isCreated()).andReturn());

        mockMvc.perform(post("/bookings/{id}/cancel", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.availableSeats").value(1));
    }

    @Test
    void expiredHoldCannotBeConfirmedAndIsReleasedBySweep() throws Exception {
        long eventId = createEvent(1);
        long seatId = seatIds(eventId).get(0);
        long bookingId = idOf(book(eventId, seatId, null).andExpect(status().isCreated()).andReturn());

        clock.advance(Duration.ofMinutes(6));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isConflict());

        bookingService.expireStaleBookings();

        mockMvc.perform(get("/bookings/{id}", bookingId))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.availableSeats").value(1));
    }

    @Test
    void reusingAKeyForADifferentRequestReturns422() throws Exception {
        long eventId = createEvent(2);
        List<Long> seatIds = seatIds(eventId);
        book(eventId, seatIds.get(0), "key-B").andExpect(status().isCreated());

        book(eventId, seatIds.get(1), "key-B").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidInputReturns400AndUnknownIdsReturn404() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"seatCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.seatCount").exists());

        mockMvc.perform(get("/events/{id}", 999999)).andExpect(status().isNotFound());
        mockMvc.perform(get("/bookings/{id}", 999999)).andExpect(status().isNotFound());
        book(999999, 1, null).andExpect(status().isNotFound());
    }

    // ---- helpers -------------------------------------------------------------------------

    private long createEvent(int seatCount) throws Exception {
        MvcResult result = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Concert\",\"seatCount\":" + seatCount + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private List<Long> seatIds(long eventId) throws Exception {
        MvcResult result = mockMvc.perform(get("/events/{id}/seats", eventId))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private ResultActions book(long eventId, long seatId, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post("/events/{id}/bookings", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatIds\":[" + seatId + "]}");
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
