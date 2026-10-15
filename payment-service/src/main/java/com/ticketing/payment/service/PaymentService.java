package com.ticketing.payment.service;

import com.ticketing.payment.dto.PaymentRequest;
import com.ticketing.payment.dto.PaymentResponse;
import com.ticketing.payment.dto.PaymentResult;
import com.ticketing.payment.exception.PaymentNotFoundException;
import com.ticketing.payment.exception.PaymentProcessingException;
import com.ticketing.payment.model.Payment;
import com.ticketing.payment.model.PaymentStatus;
import com.ticketing.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final FailureSimulator failureSimulator;

    public PaymentService(PaymentRepository paymentRepository, FailureSimulator failureSimulator) {
        this.paymentRepository = paymentRepository;
        this.failureSimulator = failureSimulator;
    }

    public PaymentResult process(PaymentRequest request) {
        Optional<Payment> existing = paymentRepository.findByBookingIdAndStatus(
                request.bookingId(), PaymentStatus.SUCCESS);
        if (existing.isPresent()) {
            return new PaymentResult(PaymentResponse.from(existing.get()), false);
        }

        failureSimulator.applyDelay();

        if (failureSimulator.shouldFail()) {
            paymentRepository.save(new Payment(request.bookingId(), request.amount(), PaymentStatus.FAILED));
            throw new PaymentProcessingException(request.bookingId());
        }

        Payment payment = paymentRepository.save(
                new Payment(request.bookingId(), request.amount(), PaymentStatus.SUCCESS));
        return new PaymentResult(PaymentResponse.from(payment), true);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findByBookingId(Long bookingId) {
        return paymentRepository.findAllByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

}
