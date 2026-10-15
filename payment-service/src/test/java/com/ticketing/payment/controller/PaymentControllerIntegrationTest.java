package com.ticketing.payment.controller;

import com.ticketing.payment.config.PaymentSimulationProperties;
import com.ticketing.payment.dto.PaymentRequest;
import com.ticketing.payment.dto.PaymentResponse;
import com.ticketing.payment.exception.ApiError;
import com.ticketing.payment.model.PaymentStatus;
import com.ticketing.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PaymentControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentSimulationProperties simulationProperties;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        client = RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void resetSimulation() {
        simulationProperties.setFailureRate(0.0);
        simulationProperties.setDelayMs(0);
    }

    @Test
    void processAndFetchPayment() {
        PaymentResponse created = client.post().uri("/api/payments")
                .body(new PaymentRequest(1L, new BigDecimal("5000.00")))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(created.createdAt()).isNotNull();

        client.get().uri("/api/payments/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentResponse.class)
                .value(payment -> assertThat(payment.amount()).isEqualByComparingTo("5000.00"));
    }

    @Test
    void repeatedRequestForSameBookingReturnsExistingPayment() {
        PaymentRequest request = new PaymentRequest(2L, new BigDecimal("1500.00"));
        PaymentResponse first = process(request);

        client.post().uri("/api/payments")
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentResponse.class)
                .value(second -> assertThat(second.id()).isEqualTo(first.id()));

        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void simulatedFailureReturnsServiceUnavailableAndRecordsAttempt() {
        simulationProperties.setFailureRate(1.0);

        client.post().uri("/api/payments")
                .body(new PaymentRequest(3L, new BigDecimal("1000.00")))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.path()).isEqualTo("/api/payments"));

        simulationProperties.setFailureRate(0.0);
        process(new PaymentRequest(3L, new BigDecimal("1000.00")));

        client.get().uri("/api/payments/booking/{bookingId}", 3L)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentResponse[].class)
                .value(attempts -> assertThat(attempts)
                        .extracting(PaymentResponse::status)
                        .containsExactly(PaymentStatus.FAILED, PaymentStatus.SUCCESS));
    }

    @Test
    void simulatedDelaySlowsDownResponse() {
        simulationProperties.setDelayMs(300);

        long start = System.nanoTime();
        process(new PaymentRequest(4L, new BigDecimal("1000.00")));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(300);
    }

    @Test
    void invalidRequestReturnsBadRequestWithFieldErrors() {
        client.post().uri("/api/payments")
                .body(new PaymentRequest(null, new BigDecimal("0.00")))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.fieldErrors()).containsKeys("bookingId", "amount"));
    }

    @Test
    void unknownPaymentReturnsNotFound() {
        client.get().uri("/api/payments/{id}", 999_999)
                .exchange()
                .expectStatus().isNotFound();
    }

    private PaymentResponse process(PaymentRequest request) {
        return client.post().uri("/api/payments")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();
    }

}
