package com.ticketing.payment.controller;

import com.ticketing.payment.dto.PaymentRequest;
import com.ticketing.payment.dto.PaymentResponse;
import com.ticketing.payment.dto.PaymentResult;
import com.ticketing.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> process(@Valid @RequestBody PaymentRequest request) {
        PaymentResult result = paymentService.process(request);
        PaymentResponse payment = result.payment();
        if (!result.created()) {
            return ResponseEntity.ok(payment);
        }
        return ResponseEntity.created(URI.create("/api/payments/" + payment.id())).body(payment);
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable Long id) {
        return paymentService.findById(id);
    }

    @GetMapping("/booking/{bookingId}")
    public List<PaymentResponse> findByBookingId(@PathVariable Long bookingId) {
        return paymentService.findByBookingId(bookingId);
    }

}
