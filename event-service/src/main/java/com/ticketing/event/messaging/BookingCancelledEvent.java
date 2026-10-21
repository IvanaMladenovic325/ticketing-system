package com.ticketing.event.messaging;

import java.time.Instant;

public record BookingCancelledEvent(
        Long bookingId,
        Long userId,
        Long eventId,
        int numberOfTickets,
        Instant occurredAt) {
}
