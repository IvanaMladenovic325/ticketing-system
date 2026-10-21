package com.ticketing.notification.controller;

import com.ticketing.notification.dto.NotificationResponse;
import com.ticketing.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> findAll() {
        return notificationService.findAll();
    }

    @GetMapping("/booking/{bookingId}")
    public List<NotificationResponse> findByBookingId(@PathVariable Long bookingId) {
        return notificationService.findByBookingId(bookingId);
    }

}
