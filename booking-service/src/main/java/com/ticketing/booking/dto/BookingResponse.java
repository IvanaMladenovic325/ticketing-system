package com.ticketing.booking.dto;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        Long id,
        Long userId,
        Long eventId,
        int numberOfTickets,
        BigDecimal totalPrice,
        BookingStatus status,
        Instant createdAt
) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getEventId(),
                booking.getNumberOfTickets(),
                booking.getTotalPrice(),
                booking.getStatus(),
                booking.getCreatedAt());
    }

}
