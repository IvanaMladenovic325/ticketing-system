package com.ticketing.payment.dto;

public record PaymentResult(PaymentResponse payment, boolean created) {
}
