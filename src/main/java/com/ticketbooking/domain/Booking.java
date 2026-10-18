package com.ticketbooking.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.ticketbooking.exception.BookingExpiredException;
import com.ticketbooking.exception.IllegalBookingStateException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "bookings",
        indexes = @Index(name = "idx_booking_status_expires", columnList = "status, expires_at"))
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    /** Unique, so a retried request can never insert a second booking. Null when the client sent no key. */
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    /** Fingerprint of (event, seats) so a reused key with a different request can be detected. */
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    /**
     * The seats this booking currently holds. The unique constraint on seat_id is the database's own
     * guarantee that a seat belongs to at most one booking at a time, even if the application checks
     * were ever bypassed. Rows are removed when the booking is cancelled or expires.
     */
    @ManyToMany
    @JoinTable(name = "booking_seats",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "seat_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_booking_seats_seat", columnNames = "seat_id"))
    private Set<Seat> seats = new LinkedHashSet<>();

    @Version
    private long version;

    protected Booking() {
        // required by JPA
    }

    public Booking(Event event, Instant createdAt, Instant expiresAt, String idempotencyKey,
                   String requestHash, Collection<Seat> seats) {
        this.event = event;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.seats.addAll(seats);
        this.seatCount = seats.size();
    }

    public void confirm(Instant now) {
        if (status == BookingStatus.PENDING && !now.isBefore(expiresAt)) {
            throw new BookingExpiredException(id);
        }
        transitionTo(BookingStatus.CONFIRMED);
        seats.forEach(Seat::markBooked);
    }

    public void cancel() {
        transitionTo(BookingStatus.CANCELLED);
        releaseSeats();
    }

    public void expire() {
        transitionTo(BookingStatus.EXPIRED);
        releaseSeats();
    }

    private void transitionTo(BookingStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalBookingStateException(id, status, target);
        }
        status = target;
    }

    private void releaseSeats() {
        seats.forEach(Seat::release);
        seats.clear();
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getSeatCount() {
        return seatCount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Set<Seat> getSeats() {
        return Collections.unmodifiableSet(seats);
    }

    public long getVersion() {
        return version;
    }
}
