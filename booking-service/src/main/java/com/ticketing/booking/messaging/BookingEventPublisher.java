package com.ticketing.booking.messaging;

import com.ticketing.booking.config.RabbitConfig;
import com.ticketing.booking.model.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishConfirmed(Booking booking) {
        send(RabbitConfig.BOOKING_CONFIRMED_KEY, booking.getId(), new BookingConfirmedEvent(
                booking.getId(), booking.getUserId(), booking.getEventId(),
                booking.getNumberOfTickets(), booking.getTotalPrice(), Instant.now()));
    }

    public void publishCancelled(Booking booking) {
        send(RabbitConfig.BOOKING_CANCELLED_KEY, booking.getId(), new BookingCancelledEvent(
                booking.getId(), booking.getUserId(), booking.getEventId(),
                booking.getNumberOfTickets(), Instant.now()));
    }

    private void send(String routingKey, Long bookingId, Object event) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.BOOKING_EXCHANGE, routingKey, event);
            log.info("Published {} for booking {}", routingKey, bookingId);
        } catch (AmqpException ex) {
            log.error("Failed to publish {} for booking {}: {}", routingKey, bookingId, ex.toString());
        }
    }

}
