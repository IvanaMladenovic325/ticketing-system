package com.ticketing.notification.client;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        String venue,
        LocalDateTime dateTime
) {
}
