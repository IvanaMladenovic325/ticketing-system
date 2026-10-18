package com.ticketing.booking.client;

import java.math.BigDecimal;

public record PaymentRequest(Long bookingId, BigDecimal amount) {
}
