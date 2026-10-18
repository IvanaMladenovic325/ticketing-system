package com.ticketing.booking.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentGateway {

    static final String RESILIENCE_NAME = "payment";

    private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);

    private final PaymentClient paymentClient;

    public PaymentGateway(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "paymentUnavailable")
    @CircuitBreaker(name = RESILIENCE_NAME)
    public PaymentOutcome pay(Long bookingId, BigDecimal amount) {
        try {
            paymentClient.process(new PaymentRequest(bookingId, amount));
            return PaymentOutcome.PAID;
        } catch (FeignException.FeignClientException ex) {
            log.warn("Payment for booking {} declined with status {}", bookingId, ex.status());
            return PaymentOutcome.DECLINED;
        }
    }

    private PaymentOutcome paymentUnavailable(Long bookingId, BigDecimal amount, Throwable cause) {
        log.warn("Payment for booking {} unavailable, booking stays pending: {}", bookingId, cause.toString());
        return PaymentOutcome.UNAVAILABLE;
    }

}
