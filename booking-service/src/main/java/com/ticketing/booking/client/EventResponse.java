package com.ticketing.booking.client;

import java.math.BigDecimal;

public record EventResponse(
        Long id,
        String name,
        int availableSeats,
        BigDecimal ticketPrice
) {
}
