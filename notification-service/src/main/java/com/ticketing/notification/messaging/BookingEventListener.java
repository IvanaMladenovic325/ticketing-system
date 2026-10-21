package com.ticketing.notification.messaging;

import com.ticketing.notification.config.RabbitConfig;
import com.ticketing.notification.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventListener {

    private final NotificationService notificationService;

    public BookingEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitConfig.BOOKING_CONFIRMED_QUEUE)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        notificationService.sendBookingConfirmed(event);
    }

    @RabbitListener(queues = RabbitConfig.BOOKING_CANCELLED_QUEUE)
    public void onBookingCancelled(BookingCancelledEvent event) {
        notificationService.sendBookingCancelled(event);
    }

}
