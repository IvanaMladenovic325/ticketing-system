package com.ticketing.event.messaging;

import com.ticketing.event.config.RabbitConfig;
import com.ticketing.event.service.SeatReservationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventListener {

    private final SeatReservationService seatReservationService;

    public BookingEventListener(SeatReservationService seatReservationService) {
        this.seatReservationService = seatReservationService;
    }

    @RabbitListener(queues = RabbitConfig.BOOKING_CONFIRMED_QUEUE)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        seatReservationService.reserveSeats(event.bookingId(), event.eventId(), event.numberOfTickets());
    }

    @RabbitListener(queues = RabbitConfig.BOOKING_CANCELLED_QUEUE)
    public void onBookingCancelled(BookingCancelledEvent event) {
        seatReservationService.releaseSeats(event.bookingId(), event.eventId(), event.numberOfTickets());
    }

}
