package com.ticketing.booking.client;

import java.math.BigDecimal;

public record PaymentResponse(
        Long id,
        Long bookingId,
        BigDecimal amount,
        String status
) {
}
