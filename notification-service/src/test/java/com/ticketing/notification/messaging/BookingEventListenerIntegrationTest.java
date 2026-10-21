package com.ticketing.notification.messaging;

import com.ticketing.notification.AbstractIntegrationTest;
import com.ticketing.notification.config.RabbitConfig;
import com.ticketing.notification.model.Notification;
import com.ticketing.notification.model.NotificationType;
import com.ticketing.notification.repository.NotificationRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BookingEventListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() throws Exception {
        amqpAdmin.purgeQueue(RabbitConfig.DEAD_LETTER_QUEUE, false);
        notificationRepository.deleteAll();
        greenMail.purgeEmailFromAllMailboxes();
        wireMock.resetAll();
        wireMock.stubFor(get(urlEqualTo("/api/users/1")).willReturn(okJson("""
                {"id":1,"firstName":"Ana","lastName":"Anić","email":"ana@example.com",
                 "createdAt":"2026-10-01T10:00:00Z"}
                """)));
        wireMock.stubFor(get(urlEqualTo("/api/events/2")).willReturn(okJson("""
                {"id":2,"name":"Koncert","description":"Test","venue":"Arena",
                 "dateTime":"2027-05-01T20:00:00","totalSeats":100,"availableSeats":97,"ticketPrice":1500.00}
                """)));
    }

    @Test
    void confirmedBookingSendsEmailToUser() throws Exception {
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(10L));

        MimeMessage mail = awaitSingleMail();
        assertThat(mail.getAllRecipients()[0].toString()).isEqualTo("ana@example.com");
        assertThat(mail.getSubject()).isEqualTo("Potvrda rezervacije #10 - Koncert");
        assertThat((String) mail.getContent()).contains("Poštovani/a Ana", "Mesto: Arena", "4500.00 RSD");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notificationRepository.findAllByBookingIdOrderBySentAtAsc(10L))
                        .singleElement()
                        .extracting(Notification::getType)
                        .isEqualTo(NotificationType.BOOKING_CONFIRMED));
    }

    @Test
    void duplicateMessageSendsOnlyOneEmail() {
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(11L));
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(11L));
        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(12L));

        await().atMost(Duration.ofSeconds(10)).until(() ->
                notificationRepository.existsByBookingIdAndType(12L, NotificationType.BOOKING_CONFIRMED));

        assertThat(greenMail.getReceivedMessages()).hasSize(2);
        assertThat(notificationRepository.findAllByBookingIdOrderBySentAtAsc(11L)).hasSize(1);
    }

    @Test
    void cancelledBookingSendsCancellationEmail() throws Exception {
        publish(RabbitConfig.BOOKING_CANCELLED_KEY, """
                {"bookingId":13,"userId":1,"eventId":2,"numberOfTickets":3,
                 "occurredAt":"2026-10-18T11:00:00Z"}
                """);

        MimeMessage mail = awaitSingleMail();
        assertThat(mail.getSubject()).isEqualTo("Otkazana rezervacija #13 - Koncert");
        assertThat((String) mail.getContent()).contains("je otkazana", "Broj otkazanih karata: 3");
    }

    @Test
    void messageEndsUpInDeadLetterQueueWhenUserServiceIsUnavailable() {
        wireMock.stubFor(get(urlEqualTo("/api/users/1")).willReturn(serviceUnavailable()));

        publish(RabbitConfig.BOOKING_CONFIRMED_KEY, confirmedJson(14L));

        Message dead = rabbitTemplate.receive(RabbitConfig.DEAD_LETTER_QUEUE, 10_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains("\"bookingId\":14");

        wireMock.verify(2, getRequestedFor(urlEqualTo("/api/users/1")));
        assertThat(greenMail.getReceivedMessages()).isEmpty();
        assertThat(notificationRepository.existsByBookingIdAndType(14L, NotificationType.BOOKING_CONFIRMED))
                .isFalse();
    }

    private void publish(String routingKey, String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setHeader("__TypeId__", "com.ticketing.booking.messaging.SomeOtherClass")
                .build();
        rabbitTemplate.send(RabbitConfig.BOOKING_EXCHANGE, routingKey, message);
    }

    private static MimeMessage awaitSingleMail() {
        await().atMost(Duration.ofSeconds(10)).until(() -> greenMail.getReceivedMessages().length == 1);
        return greenMail.getReceivedMessages()[0];
    }

    private static String confirmedJson(long bookingId) {
        return """
                {"bookingId":%d,"userId":1,"eventId":2,"numberOfTickets":3,
                 "totalPrice":4500.00,"occurredAt":"2026-10-18T10:00:00Z"}
                """.formatted(bookingId);
    }

}
