package com.ticketing.notification.client;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email
) {
}
