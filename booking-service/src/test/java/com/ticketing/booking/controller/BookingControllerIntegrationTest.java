package com.ticketing.booking.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.dto.CreateBookingRequest;
import com.ticketing.booking.config.RabbitConfig;
import com.ticketing.booking.exception.ApiError;
import com.ticketing.booking.messaging.BookingCancelledEvent;
import com.ticketing.booking.messaging.BookingConfirmedEvent;
import com.ticketing.booking.model.BookingStatus;
import com.ticketing.booking.repository.BookingRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class BookingControllerIntegrationTest {

    private static final String PAYMENTS = "/api/payments";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

    private static final String TEST_QUEUE = "booking-service-test.events";

    static WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void downstreamServices(DynamicPropertyRegistry registry) {
        for (String service : new String[]{"user-service", "event-service", "payment-service"}) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
                    wireMock::baseUrl);
        }
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TopicExchange bookingExchange;

    @Autowired
    private JsonMapper jsonMapper;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("payment").reset();
        Queue testQueue = new Queue(TEST_QUEUE, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(bookingExchange).with("booking.#"));
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
        client = RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();

        stubUser(1L);
        stubEvent(2L, 100, "1500.00");
    }

    @Test
    void createBookingIsConfirmedWhenPaymentSucceeds() {
        wireMock.stubFor(post(PAYMENTS).willReturn(paymentCreated()));

        BookingResponse booking = createBooking(new CreateBookingRequest(1L, 2L, 2));

        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.totalPrice()).isEqualByComparingTo("3000.00");

        BookingConfirmedEvent event = receiveEvent(RabbitConfig.BOOKING_CONFIRMED_KEY, BookingConfirmedEvent.class);
        assertThat(event.bookingId()).isEqualTo(booking.id());
        assertThat(event.eventId()).isEqualTo(2L);
        assertThat(event.numberOfTickets()).isEqualTo(2);
        assertThat(event.totalPrice()).isEqualByComparingTo("3000.00");
        wireMock.verify(1, postRequestedFor(urlEqualTo(PAYMENTS))
                .withRequestBody(equalToJson("{\"bookingId\": " + booking.id() + ", \"amount\": 3000.00}")));

        client.get().uri("/api/bookings/{id}", booking.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BookingResponse.class)
                .value(found -> assertThat(found.status()).isEqualTo(BookingStatus.CONFIRMED));
    }

    @Test
    void paymentServerErrorIsRetriedAndBookingStaysPending() {
        wireMock.stubFor(post(PAYMENTS).willReturn(aResponse().withStatus(503)));

        BookingResponse booking = createBooking(new CreateBookingRequest(1L, 2L, 1));

        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        wireMock.verify(3, postRequestedFor(urlEqualTo(PAYMENTS)));
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 500)).isNull();
    }

    @Test
    void paymentTimeoutFallsBackToPending() {
        wireMock.stubFor(post(PAYMENTS).willReturn(paymentCreated().withFixedDelay(1_000)));

        BookingResponse booking = createBooking(new CreateBookingRequest(1L, 2L, 1));

        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void declinedPaymentIsNotRetriedAndMarksBookingFailed() {
        wireMock.stubFor(post(PAYMENTS).willReturn(aResponse().withStatus(400)));

        BookingResponse booking = createBooking(new CreateBookingRequest(1L, 2L, 1));

        assertThat(booking.status()).isEqualTo(BookingStatus.PAYMENT_FAILED);
        wireMock.verify(1, postRequestedFor(urlEqualTo(PAYMENTS)));
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailuresAndSkipsPaymentCalls() {
        wireMock.stubFor(post(PAYMENTS).willReturn(serverError()));

        createBooking(new CreateBookingRequest(1L, 2L, 1));
        createBooking(new CreateBookingRequest(1L, 2L, 1));

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("payment");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        wireMock.verify(4, postRequestedFor(urlEqualTo(PAYMENTS)));

        BookingResponse booking = createBooking(new CreateBookingRequest(1L, 2L, 1));
        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        wireMock.verify(4, postRequestedFor(urlEqualTo(PAYMENTS)));

        client.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(metrics -> assertThat(metrics)
                        .containsPattern("resilience4j_circuitbreaker_state\\{.*name=\"payment\",state=\"open\"} 1.0"));
    }

    @Test
    void payEndpointConfirmsPendingBookingOncePaymentServiceRecovers() {
        wireMock.stubFor(post(PAYMENTS).willReturn(aResponse().withStatus(503)));
        BookingResponse pending = createBooking(new CreateBookingRequest(1L, 2L, 1));
        assertThat(pending.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        wireMock.stubFor(post(PAYMENTS).willReturn(paymentCreated()));

        client.post().uri("/api/bookings/{id}/pay", pending.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BookingResponse.class)
                .value(paid -> assertThat(paid.status()).isEqualTo(BookingStatus.CONFIRMED));

        client.post().uri("/api/bookings/{id}/pay", pending.id())
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void freeEventIsConfirmedWithoutPayment() {
        stubEvent(3L, 50, "0.00");

        BookingResponse booking = createBooking(new CreateBookingRequest(1L, 3L, 2));

        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        wireMock.verify(0, postRequestedFor(urlEqualTo(PAYMENTS)));
    }

    @Test
    void cancelBookingAndRejectSecondCancellation() {
        wireMock.stubFor(post(PAYMENTS).willReturn(paymentCreated()));
        BookingResponse booking = createBooking(new CreateBookingRequest(1L, 2L, 1));

        receiveEvent(RabbitConfig.BOOKING_CONFIRMED_KEY, BookingConfirmedEvent.class);

        client.put().uri("/api/bookings/{id}/cancel", booking.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BookingResponse.class)
                .value(cancelled -> assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED));

        BookingCancelledEvent event = receiveEvent(RabbitConfig.BOOKING_CANCELLED_KEY, BookingCancelledEvent.class);
        assertThat(event.bookingId()).isEqualTo(booking.id());
        assertThat(event.numberOfTickets()).isEqualTo(1);

        client.put().uri("/api/bookings/{id}/cancel", booking.id())
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void findBookingsByUser() {
        wireMock.stubFor(post(PAYMENTS).willReturn(paymentCreated()));
        createBooking(new CreateBookingRequest(1L, 2L, 1));
        createBooking(new CreateBookingRequest(1L, 2L, 2));

        client.get().uri("/api/bookings/user/{userId}", 1L)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BookingResponse[].class)
                .value(bookings -> assertThat(bookings).hasSize(2));
    }

    @Test
    void unknownUserReturnsNotFound() {
        wireMock.stubFor(get("/api/users/99").willReturn(notFound()));

        client.post().uri("/api/bookings")
                .body(new CreateBookingRequest(99L, 2L, 1))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.message()).contains("User with id 99"));

        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void notEnoughSeatsReturnsConflict() {
        stubEvent(4L, 1, "1500.00");

        client.post().uri("/api/bookings")
                .body(new CreateBookingRequest(1L, 4L, 2))
                .exchange()
                .expectStatus().isEqualTo(409);

        wireMock.verify(0, postRequestedFor(urlEqualTo(PAYMENTS)));
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void unavailableUserServiceReturnsServiceUnavailable() {
        wireMock.stubFor(get("/api/users/5").willReturn(serverError()));

        client.post().uri("/api/bookings")
                .body(new CreateBookingRequest(5L, 2L, 1))
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void invalidRequestReturnsBadRequestWithFieldErrors() {
        client.post().uri("/api/bookings")
                .body(new CreateBookingRequest(null, 2L, 0))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.fieldErrors()).containsKeys("userId", "numberOfTickets"));
    }

    @Test
    void unknownBookingReturnsNotFound() {
        client.get().uri("/api/bookings/{id}", 999_999)
                .exchange()
                .expectStatus().isNotFound();
    }

    private BookingResponse createBooking(CreateBookingRequest request) {
        return client.post().uri("/api/bookings")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BookingResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private <T> T receiveEvent(String expectedRoutingKey, Class<T> type) {
        Message message = rabbitTemplate.receive(TEST_QUEUE, 5_000);
        assertThat(message).as("message with routing key " + expectedRoutingKey).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(expectedRoutingKey);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        return jsonMapper.readValue(message.getBody(), type);
    }

    private static void stubUser(long id) {
        wireMock.stubFor(get("/api/users/" + id).willReturn(okJson("""
                {"id": %d, "firstName": "Ana", "lastName": "Anić", "email": "ana@example.com",
                 "createdAt": "2026-01-01T10:00:00Z"}
                """.formatted(id))));
    }

    private static void stubEvent(long id, int availableSeats, String ticketPrice) {
        wireMock.stubFor(get("/api/events/" + id).willReturn(okJson("""
                {"id": %d, "name": "Koncert", "description": "Opis", "venue": "Arena",
                 "dateTime": "2027-05-01T20:00:00", "totalSeats": 100, "availableSeats": %d,
                 "ticketPrice": %s}
                """.formatted(id, availableSeats, ticketPrice))));
    }

    private static ResponseDefinitionBuilder paymentCreated() {
        return aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": 1, "bookingId": 1, "amount": 1500.00, "status": "SUCCESS",
                         "createdAt": "2026-01-01T10:00:00Z"}
                        """);
    }

}
