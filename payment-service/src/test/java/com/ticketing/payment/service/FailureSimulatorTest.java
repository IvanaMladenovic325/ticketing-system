package com.ticketing.payment.service;

import com.ticketing.payment.config.PaymentSimulationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailureSimulatorTest {

    @Mock
    private RandomGenerator random;

    private PaymentSimulationProperties properties;
    private FailureSimulator simulator;

    @BeforeEach
    void setUp() {
        properties = new PaymentSimulationProperties();
        simulator = new FailureSimulator(properties, random);
    }

    @Test
    void neverFailsWhenFailureRateIsZero() {
        properties.setFailureRate(0.0);

        assertThat(simulator.shouldFail()).isFalse();
        verifyNoInteractions(random);
    }

    @Test
    void failsWhenRandomValueIsBelowFailureRate() {
        properties.setFailureRate(0.3);
        when(random.nextDouble()).thenReturn(0.29);

        assertThat(simulator.shouldFail()).isTrue();
    }

    @Test
    void succeedsWhenRandomValueIsAtOrAboveFailureRate() {
        properties.setFailureRate(0.3);
        when(random.nextDouble()).thenReturn(0.3);

        assertThat(simulator.shouldFail()).isFalse();
    }

    @Test
    void alwaysFailsWhenFailureRateIsOne() {
        properties.setFailureRate(1.0);
        when(random.nextDouble()).thenReturn(0.999_999);

        assertThat(simulator.shouldFail()).isTrue();
    }

    @Test
    void applyDelayWaitsConfiguredTime() {
        properties.setDelayMs(100);

        long start = System.nanoTime();
        simulator.applyDelay();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(100);
    }

}
