package com.ticketing.booking.client;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email
) {
}
