package com.ticketing.notification.service;

import com.ticketing.notification.client.EventClient;
import com.ticketing.notification.client.EventResponse;
import com.ticketing.notification.client.UserClient;
import com.ticketing.notification.client.UserResponse;
import com.ticketing.notification.config.NotificationMailProperties;
import com.ticketing.notification.messaging.BookingCancelledEvent;
import com.ticketing.notification.messaging.BookingConfirmedEvent;
import com.ticketing.notification.model.Notification;
import com.ticketing.notification.model.NotificationType;
import com.ticketing.notification.repository.NotificationRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UserResponse USER = new UserResponse(1L, "Ana", "Anić", "ana@example.com");
    private static final EventResponse EVENT =
            new EventResponse(2L, "Koncert", "Arena", LocalDateTime.of(2027, 5, 1, 20, 0));
    private static final BookingConfirmedEvent CONFIRMED =
            new BookingConfirmedEvent(10L, 1L, 2L, 3, new BigDecimal("4500.00"), Instant.now());
    private static final BookingCancelledEvent CANCELLED =
            new BookingCancelledEvent(10L, 1L, 2L, 3, Instant.now());

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private EventClient eventClient;

    @Mock
    private MailSender mailSender;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userClient, eventClient, mailSender,
                new NotificationMailProperties("no-reply@ticketing.local"));
    }

    @Test
    void confirmedBookingSendsEmailAndStoresNotification() {
        when(userClient.findById(1L)).thenReturn(USER);
        when(eventClient.findById(2L)).thenReturn(EVENT);

        notificationService.sendBookingConfirmed(CONFIRMED);

        SimpleMailMessage mail = captureSentMail();
        assertThat(mail.getTo()).containsExactly("ana@example.com");
        assertThat(mail.getFrom()).isEqualTo("no-reply@ticketing.local");
        assertThat(mail.getSubject()).contains("Potvrda rezervacije #10", "Koncert");
        assertThat(mail.getText()).contains("Ana", "Arena", "01.05.2027. 20:00", "Broj karata: 3", "4500.00 RSD");

        Notification saved = captureSavedNotification();
        assertThat(saved.getBookingId()).isEqualTo(10L);
        assertThat(saved.getRecipientEmail()).isEqualTo("ana@example.com");
        assertThat(saved.getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
    }

    @Test
    void cancelledBookingSendsCancellationEmail() {
        when(userClient.findById(1L)).thenReturn(USER);
        when(eventClient.findById(2L)).thenReturn(EVENT);

        notificationService.sendBookingCancelled(CANCELLED);

        SimpleMailMessage mail = captureSentMail();
        assertThat(mail.getSubject()).contains("Otkazana rezervacija #10");
        assertThat(mail.getText()).contains("otkazana", "Broj otkazanih karata: 3");
        assertThat(captureSavedNotification().getType()).isEqualTo(NotificationType.BOOKING_CANCELLED);
    }

    @Test
    void duplicateMessageIsSkippedWithoutCallingOtherServices() {
        when(notificationRepository.existsByBookingIdAndType(10L, NotificationType.BOOKING_CONFIRMED))
                .thenReturn(true);

        notificationService.sendBookingConfirmed(CONFIRMED);

        verifyNoInteractions(userClient, eventClient, mailSender);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void unavailableUserServicePropagatesErrorSoMessageIsRetried() {
        when(userClient.findById(1L)).thenThrow(serviceUnavailable());

        assertThatThrownBy(() -> notificationService.sendBookingConfirmed(CONFIRMED))
                .isInstanceOf(FeignException.ServiceUnavailable.class);

        verifyNoInteractions(mailSender);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void failedEmailIsNotStoredAsSent() {
        when(userClient.findById(1L)).thenReturn(USER);
        when(eventClient.findById(2L)).thenReturn(EVENT);
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notificationService.sendBookingConfirmed(CONFIRMED))
                .isInstanceOf(MailSendException.class);

        verify(notificationRepository, never()).save(any());
    }

    private SimpleMailMessage captureSentMail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private Notification captureSavedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private static FeignException serviceUnavailable() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/users/1", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return new FeignException.ServiceUnavailable("user-service unavailable", request, null, Map.of());
    }

}
