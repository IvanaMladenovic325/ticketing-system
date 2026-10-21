package com.ticketing.notification.repository;

import com.ticketing.notification.model.Notification;
import com.ticketing.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByBookingIdAndType(Long bookingId, NotificationType type);

    List<Notification> findAllByBookingIdOrderBySentAtAsc(Long bookingId);

    List<Notification> findAllByOrderBySentAtDesc();

}
