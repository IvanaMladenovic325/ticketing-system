package com.ticketing.event.messaging;

import com.ticketing.event.AbstractIntegrationTest;
import com.ticketing.event.config.RabbitConfig;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.SeatReservationStatus;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BookingEventListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatReservationRepository reservationRepository;

    private Long eventId;

    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue(RabbitConfig.DEAD_LETTER_QUEUE, false);
        reservationRepository.deleteAll();
        eventRepository.deleteAll();
        eventId = eventRepository.save(new Event("Koncert", null, "Arena",
                LocalDateTime.now().plusMonths(1), 100, new BigDecimal("1500.00"))).getId();
    }

    @Test
    void confirmedBookingDecreasesAvailableSeats() {
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(1L, eventId, 3));

        awaitAvailableSeats(97);
        assertThat(reservationRepository.findById(1L))
                .hasValueSatisfying(r -> assertThat(r.getStatus()).isEqualTo(SeatReservationStatus.RESERVED));
    }

    @Test
    void duplicateConfirmedMessageIsProcessedOnlyOnce() {
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(2L, eventId, 5));
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(2L, eventId, 5));
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(3L, eventId, 1));

        await().atMost(Duration.ofSeconds(10)).until(() -> reservationRepository.existsById(3L));
        awaitAvailableSeats(94);
    }

    @Test
    void cancelledBookingReturnsSeats() {
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(4L, eventId, 10));
        awaitAvailableSeats(90);

        publish(RabbitConfig.BOOKING_CANCELLED_KEY, cancelledJson(4L, eventId, 10));

        awaitAvailableSeats(100);
        assertThat(reservationRepository.findById(4L))
                .hasValueSatisfying(r -> assertThat(r.getStatus()).isEqualTo(SeatReservationStatus.RELEASED));
    }

    @Test
    void messageThatCannotBeProcessedEndsUpInDeadLetterQueue() {
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(5L, 999_999L, 1));

        Message dead = rabbitTemplate.receive(RabbitConfig.DEAD_LETTER_QUEUE, 10_000);

        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains("\"bookingId\":5");
        assertThat(reservationRepository.existsById(5L)).isFalse();
    }

    private void publish(String routingKey, String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setHeader("__TypeId__", "com.ticketing.booking.messaging.SomeOtherClass")
                .build();
        rabbitTemplate.send(RabbitConfig.BOOKING_EXCHANGE, routingKey, message);
    }

    private void awaitAvailableSeats(int expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(eventRepository.findById(eventId).orElseThrow().getAvailableSeats()).isEqualTo(expected));
    }

    private static String confirmedJson(long bookingId, long eventId, int tickets) {
        return """
                {"bookingId":%d,"userId":1,"eventId":%d,"numberOfTickets":%d,
                 "totalPrice":4500.00,"occurredAt":"2026-10-18T10:00:00Z"}
                """.formatted(bookingId, eventId, tickets);
    }

    private static String cancelledJson(long bookingId, long eventId, int tickets) {
        return """
                {"bookingId":%d,"userId":1,"eventId":%d,"numberOfTickets":%d,
                 "occurredAt":"2026-10-18T11:00:00Z"}
                """.formatted(bookingId, eventId, tickets);
    }

}
