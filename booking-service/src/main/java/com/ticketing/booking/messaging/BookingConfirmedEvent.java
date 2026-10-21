package com.ticketing.booking.messaging;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingConfirmedEvent(
        Long bookingId,
        Long userId,
        Long eventId,
        int numberOfTickets,
        BigDecimal totalPrice,
        Instant occurredAt) {
}
