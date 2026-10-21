package com.ticketing.notification.dto;

import com.ticketing.notification.model.Notification;
import com.ticketing.notification.model.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long bookingId,
        String recipientEmail,
        NotificationType type,
        Instant sentAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getBookingId(),
                notification.getRecipientEmail(),
                notification.getType(),
                notification.getSentAt());
    }

}
