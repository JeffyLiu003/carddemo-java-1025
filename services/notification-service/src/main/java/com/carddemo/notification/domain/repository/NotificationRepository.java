package com.carddemo.notification.domain.repository;

import com.carddemo.notification.domain.model.Notification;
import com.carddemo.notification.domain.model.NotificationStatus;

import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(String notificationId);

    PageResult<Notification> search(NotificationStatus status, int page, int size);
}
