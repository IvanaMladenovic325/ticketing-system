package com.ticketing.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private Long bookingId;

    @Column(name = "recipient_email", nullable = false, updatable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private NotificationType type;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected Notification() {
        // potreban JPA-u
    }

    public Notification(Long bookingId, String recipientEmail, NotificationType type) {
        this.bookingId = bookingId;
        this.recipientEmail = recipientEmail;
        this.type = type;
    }

    @PrePersist
    void onCreate() {
        sentAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public NotificationType getType() {
        return type;
    }

    public Instant getSentAt() {
        return sentAt;
    }

}
