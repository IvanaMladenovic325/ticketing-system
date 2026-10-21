package com.ticketing.notification.service;

import com.ticketing.notification.client.EventClient;
import com.ticketing.notification.client.EventResponse;
import com.ticketing.notification.client.UserClient;
import com.ticketing.notification.client.UserResponse;
import com.ticketing.notification.config.NotificationMailProperties;
import com.ticketing.notification.dto.NotificationResponse;
import com.ticketing.notification.messaging.BookingCancelledEvent;
import com.ticketing.notification.messaging.BookingConfirmedEvent;
import com.ticketing.notification.model.Notification;
import com.ticketing.notification.model.NotificationType;
import com.ticketing.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm");

    private final NotificationRepository notificationRepository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final MailSender mailSender;
    private final NotificationMailProperties mailProperties;

    public NotificationService(NotificationRepository notificationRepository, UserClient userClient,
                               EventClient eventClient, MailSender mailSender,
                               NotificationMailProperties mailProperties) {
        this.notificationRepository = notificationRepository;
        this.userClient = userClient;
        this.eventClient = eventClient;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    public void sendBookingConfirmed(BookingConfirmedEvent event) {
        if (alreadySent(event.bookingId(), NotificationType.BOOKING_CONFIRMED)) {
            return;
        }
        UserResponse user = userClient.findById(event.userId());
        EventResponse ticketedEvent = eventClient.findById(event.eventId());

        String subject = "Potvrda rezervacije #" + event.bookingId() + " - " + ticketedEvent.name();
        String text = """
                Poštovani/a %s,

                vaša rezervacija je potvrđena.

                Događaj: %s
                Mesto: %s
                Vreme: %s
                Broj karata: %d
                Ukupno plaćeno: %s RSD

                Broj rezervacije: %d
                """.formatted(user.firstName(), ticketedEvent.name(), ticketedEvent.venue(),
                ticketedEvent.dateTime().format(DATE_TIME), event.numberOfTickets(),
                event.totalPrice().toPlainString(), event.bookingId());

        send(event.bookingId(), user.email(), NotificationType.BOOKING_CONFIRMED, subject, text);
    }

    public void sendBookingCancelled(BookingCancelledEvent event) {
        if (alreadySent(event.bookingId(), NotificationType.BOOKING_CANCELLED)) {
            return;
        }
        UserResponse user = userClient.findById(event.userId());
        EventResponse ticketedEvent = eventClient.findById(event.eventId());

        String subject = "Otkazana rezervacija #" + event.bookingId() + " - " + ticketedEvent.name();
        String text = """
                Poštovani/a %s,

                vaša rezervacija za događaj "%s" (%s) je otkazana.
                Broj otkazanih karata: %d

                Broj rezervacije: %d
                """.formatted(user.firstName(), ticketedEvent.name(),
                ticketedEvent.dateTime().format(DATE_TIME), event.numberOfTickets(), event.bookingId());

        send(event.bookingId(), user.email(), NotificationType.BOOKING_CANCELLED, subject, text);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findAll() {
        return notificationRepository.findAllByOrderBySentAtDesc().stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findByBookingId(Long bookingId) {
        return notificationRepository.findAllByBookingIdOrderBySentAtAsc(bookingId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    private boolean alreadySent(Long bookingId, NotificationType type) {
        if (notificationRepository.existsByBookingIdAndType(bookingId, type)) {
            log.info("{} notification for booking {} already sent, skipping", type, bookingId);
            return true;
        }
        return false;
    }

    private void send(Long bookingId, String recipient, NotificationType type, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.from());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);

        notificationRepository.save(new Notification(bookingId, recipient, type));
        log.info("Sent {} email for booking {} to {}", type, bookingId, recipient);
    }

}
