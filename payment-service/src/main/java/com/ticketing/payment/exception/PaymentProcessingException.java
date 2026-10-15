package com.ticketing.payment.exception;

public class PaymentProcessingException extends RuntimeException {

    public PaymentProcessingException(Long bookingId) {
        super("Payment for booking " + bookingId + " could not be processed, please try again later");
    }

}
