package com.ticketing.event.dto;

import com.ticketing.event.model.Event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        String description,
        String venue,
        LocalDateTime dateTime,
        int totalSeats,
        int availableSeats,
        BigDecimal ticketPrice
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getVenue(),
                event.getDateTime(),
                event.getTotalSeats(),
                event.getAvailableSeats(),
                event.getTicketPrice());
    }

}
