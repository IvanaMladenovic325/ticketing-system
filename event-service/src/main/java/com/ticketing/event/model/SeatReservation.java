package com.ticketing.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "seat_reservations")
public class SeatReservation {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "number_of_tickets", nullable = false, updatable = false)
    private int numberOfTickets;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatReservationStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SeatReservation() {
        // potreban JPA-u
    }

    public SeatReservation(Long bookingId, Long eventId, int numberOfTickets, SeatReservationStatus status) {
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.numberOfTickets = numberOfTickets;
        this.status = status;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getEventId() {
        return eventId;
    }

    public int getNumberOfTickets() {
        return numberOfTickets;
    }

    public SeatReservationStatus getStatus() {
        return status;
    }

    public void markReleased() {
        this.status = SeatReservationStatus.RELEASED;
    }

}
