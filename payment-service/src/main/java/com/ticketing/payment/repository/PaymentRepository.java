package com.ticketing.payment.repository;

import com.ticketing.payment.model.Payment;
import com.ticketing.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    List<Payment> findAllByBookingIdOrderByCreatedAtAsc(Long bookingId);

}
