package com.ticketing.event.controller;

import com.ticketing.event.dto.CreateEventRequest;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.dto.UpdateEventRequest;
import com.ticketing.event.exception.ApiError;
import com.ticketing.event.model.Event;
import com.ticketing.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class EventControllerIntegrationTest {

    private static final LocalDateTime DATE_TIME = LocalDateTime.now().plusMonths(1).withNano(0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private EventRepository eventRepository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void createAndFetchEvent() {
        EventResponse created = client.post().uri("/api/events")
                .body(new CreateEventRequest("Koncert", "Rok koncert", "Arena", DATE_TIME, 100,
                        new BigDecimal("2500.00")))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody(EventResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.availableSeats()).isEqualTo(100);
        assertThat(created.dateTime()).isEqualTo(DATE_TIME);

        client.get().uri("/api/events/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(EventResponse.class)
                .value(event -> assertThat(event.ticketPrice()).isEqualByComparingTo("2500.00"));
    }

    @Test
    void findAllReturnsEventsOrderedByDate() {
        createEvent("Kasniji", DATE_TIME.plusDays(10));
        createEvent("Raniji", DATE_TIME);

        client.get().uri("/api/events")
                .exchange()
                .expectStatus().isOk()
                .expectBody(EventResponse[].class)
                .value(events -> assertThat(events)
                        .extracting(EventResponse::name)
                        .containsExactly("Raniji", "Kasniji"));
    }

    @Test
    void invalidRequestReturnsBadRequestWithFieldErrors() {
        client.post().uri("/api/events")
                .body(new CreateEventRequest("", null, "Arena", LocalDateTime.now().minusDays(1), 0,
                        new BigDecimal("-1")))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.fieldErrors())
                        .containsKeys("name", "dateTime", "totalSeats", "ticketPrice"));
    }

    @Test
    void unknownEventReturnsNotFound() {
        client.get().uri("/api/events/{id}", 999_999)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.path()).isEqualTo("/api/events/999999"));
    }

    @Test
    void updateEventKeepsSoldSeats() {
        EventResponse created = createEvent("Koncert", DATE_TIME);
        sellSeats(created.id(), 30);

        client.put().uri("/api/events/{id}", created.id())
                .body(new UpdateEventRequest("Koncert", null, "Nova arena", DATE_TIME, 150,
                        new BigDecimal("3000.00")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(EventResponse.class)
                .value(event -> {
                    assertThat(event.venue()).isEqualTo("Nova arena");
                    assertThat(event.totalSeats()).isEqualTo(150);
                    assertThat(event.availableSeats()).isEqualTo(120);
                });

        client.put().uri("/api/events/{id}", created.id())
                .body(new UpdateEventRequest("Koncert", null, "Nova arena", DATE_TIME, 20,
                        new BigDecimal("3000.00")))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void deleteEvent() {
        EventResponse free = createEvent("Bez prodatih karata", DATE_TIME);
        EventResponse sold = createEvent("Sa prodatim kartama", DATE_TIME);
        sellSeats(sold.id(), 1);

        client.delete().uri("/api/events/{id}", free.id())
                .exchange()
                .expectStatus().isNoContent();
        client.delete().uri("/api/events/{id}", sold.id())
                .exchange()
                .expectStatus().isEqualTo(409);

        assertThat(eventRepository.existsById(free.id())).isFalse();
        assertThat(eventRepository.existsById(sold.id())).isTrue();
    }

    private EventResponse createEvent(String name, LocalDateTime dateTime) {
        return client.post().uri("/api/events")
                .body(new CreateEventRequest(name, null, "Arena", dateTime, 100, new BigDecimal("2500.00")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(EventResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void sellSeats(Long eventId, int seats) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        ReflectionTestUtils.setField(event, "availableSeats", event.getAvailableSeats() - seats);
        eventRepository.save(event);
    }

}
