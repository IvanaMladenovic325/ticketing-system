package com.ticketing.notification.controller;

import com.ticketing.notification.AbstractIntegrationTest;
import com.ticketing.notification.dto.NotificationResponse;
import com.ticketing.notification.exception.ApiError;
import com.ticketing.notification.model.Notification;
import com.ticketing.notification.model.NotificationType;
import com.ticketing.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final ParameterizedTypeReference<List<NotificationResponse>> LIST =
            new ParameterizedTypeReference<>() {
            };

    @LocalServerPort
    private int port;

    @Autowired
    private NotificationRepository notificationRepository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        client = RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void findByBookingIdReturnsNotificationsForThatBooking() {
        notificationRepository.save(new Notification(1L, "ana@example.com", NotificationType.BOOKING_CONFIRMED));
        notificationRepository.save(new Notification(1L, "ana@example.com", NotificationType.BOOKING_CANCELLED));
        notificationRepository.save(new Notification(2L, "marko@example.com", NotificationType.BOOKING_CONFIRMED));

        List<NotificationResponse> notifications = client.get().uri("/api/notifications/booking/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(LIST)
                .returnResult()
                .getResponseBody();

        assertThat(notifications)
                .extracting(NotificationResponse::type)
                .containsExactly(NotificationType.BOOKING_CONFIRMED, NotificationType.BOOKING_CANCELLED);
        assertThat(notifications).allSatisfy(n -> {
            assertThat(n.bookingId()).isEqualTo(1L);
            assertThat(n.recipientEmail()).isEqualTo("ana@example.com");
            assertThat(n.sentAt()).isNotNull();
        });
    }

    @Test
    void findAllReturnsEveryNotification() {
        notificationRepository.save(new Notification(1L, "ana@example.com", NotificationType.BOOKING_CONFIRMED));
        notificationRepository.save(new Notification(2L, "marko@example.com", NotificationType.BOOKING_CONFIRMED));

        List<NotificationResponse> notifications = client.get().uri("/api/notifications")
                .exchange()
                .expectStatus().isOk()
                .expectBody(LIST)
                .returnResult()
                .getResponseBody();

        assertThat(notifications).extracting(NotificationResponse::bookingId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void invalidBookingIdReturnsBadRequest() {
        ApiError error = client.get().uri("/api/notifications/booking/abc")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody(ApiError.class)
                .returnResult()
                .getResponseBody();

        assertThat(error.message()).contains("abc", "bookingId");
    }

}
