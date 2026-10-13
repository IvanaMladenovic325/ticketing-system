package com.ticketing.event.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 200) String venue,
        @NotNull @Future LocalDateTime dateTime,
        @NotNull @Positive Integer totalSeats,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal ticketPrice
) {
}
