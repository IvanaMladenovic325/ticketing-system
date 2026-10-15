package com.ticketing.payment.service;

import com.ticketing.payment.config.PaymentSimulationProperties;
import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

@Component
public class FailureSimulator {

    private final PaymentSimulationProperties properties;
    private final RandomGenerator random;

    public FailureSimulator(PaymentSimulationProperties properties, RandomGenerator random) {
        this.properties = properties;
        this.random = random;
    }

    public void applyDelay() {
        long delayMs = properties.getDelayMs();
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean shouldFail() {
        double failureRate = properties.getFailureRate();
        return failureRate > 0 && random.nextDouble() < failureRate;
    }

}
