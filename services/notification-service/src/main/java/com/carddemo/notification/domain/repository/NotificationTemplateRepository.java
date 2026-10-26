package com.carddemo.notification.domain.repository;

import com.carddemo.notification.domain.model.NotificationTemplate;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository {

    List<NotificationTemplate> findAll();

    Optional<NotificationTemplate> findByCode(String templateCode);

    NotificationTemplate save(NotificationTemplate template);
}
