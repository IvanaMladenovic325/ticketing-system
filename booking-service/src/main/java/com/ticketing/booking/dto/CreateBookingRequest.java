package com.ticketing.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBookingRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long eventId,
        @NotNull @Min(1) @Max(10) Integer numberOfTickets
) {
}
