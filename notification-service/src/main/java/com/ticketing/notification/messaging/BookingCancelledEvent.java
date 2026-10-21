package com.ticketing.notification.messaging;

import java.time.Instant;

public record BookingCancelledEvent(
        Long bookingId,
        Long userId,
        Long eventId,
        int numberOfTickets,
        Instant occurredAt) {
}
