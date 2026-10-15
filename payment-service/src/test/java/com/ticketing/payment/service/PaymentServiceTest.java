package com.ticketing.payment.service;

import com.ticketing.payment.dto.PaymentRequest;
import com.ticketing.payment.dto.PaymentResult;
import com.ticketing.payment.exception.PaymentNotFoundException;
import com.ticketing.payment.exception.PaymentProcessingException;
import com.ticketing.payment.model.Payment;
import com.ticketing.payment.model.PaymentStatus;
import com.ticketing.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final PaymentRequest REQUEST = new PaymentRequest(7L, new BigDecimal("5000.00"));

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private FailureSimulator failureSimulator;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processSavesSuccessfulPayment() {
        when(paymentRepository.findByBookingIdAndStatus(7L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(failureSimulator.shouldFail()).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResult result = paymentService.process(REQUEST);

        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.payment().bookingId()).isEqualTo(7L);
        assertThat(result.payment().amount()).isEqualByComparingTo("5000.00");

        InOrder order = inOrder(failureSimulator, paymentRepository);
        order.verify(failureSimulator).applyDelay();
        order.verify(failureSimulator).shouldFail();
        order.verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void processRecordsFailedAttemptAndThrowsWhenSimulationFails() {
        when(paymentRepository.findByBookingIdAndStatus(7L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(failureSimulator.shouldFail()).thenReturn(true);

        assertThatThrownBy(() -> paymentService.process(REQUEST))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("7");

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void processReturnsExistingSuccessfulPaymentWithoutChargingAgain() {
        Payment existing = new Payment(7L, new BigDecimal("5000.00"), PaymentStatus.SUCCESS);
        when(paymentRepository.findByBookingIdAndStatus(7L, PaymentStatus.SUCCESS)).thenReturn(Optional.of(existing));

        PaymentResult result = paymentService.process(REQUEST);

        assertThat(result.created()).isFalse();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(failureSimulator);
    }

    @Test
    void findByIdThrowsWhenPaymentDoesNotExist() {
        when(paymentRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findById(42L))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("42");
    }

}
