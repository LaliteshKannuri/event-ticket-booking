package com.ticketbooking.domain;

import com.ticketbooking.exception.SeatNotAvailableException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_seat_event_label", columnNames = {"event_id", "label"}))
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false, length = 20)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatStatus status = SeatStatus.AVAILABLE;

    /**
     * Optimistic lock. Every UPDATE on a seat is "... WHERE id = ? AND version = ?", so if two
     * transactions read the same seat and both try to change it, only the first one to write
     * succeeds and the other one fails instead of silently overwriting.
     */
    @Version
    private long version;

    protected Seat() {
        // required by JPA
    }

    public Seat(Event event, String label) {
        this.event = event;
        this.label = label;
    }

    public void hold() {
        if (status != SeatStatus.AVAILABLE) {
            throw new SeatNotAvailableException(id, label);
        }
        status = SeatStatus.HELD;
    }

    public void markBooked() {
        if (status != SeatStatus.HELD) {
            throw new IllegalStateException(
                    "Seat " + label + " must be HELD before it can be booked, but is " + status);
        }
        status = SeatStatus.BOOKED;
    }

    public void release() {
        status = SeatStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getLabel() {
        return label;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
