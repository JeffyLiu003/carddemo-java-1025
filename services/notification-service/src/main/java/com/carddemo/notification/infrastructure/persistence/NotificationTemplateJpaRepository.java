package com.carddemo.notification.infrastructure.persistence;

import com.carddemo.notification.domain.model.NotificationTemplate;
import com.carddemo.notification.domain.repository.NotificationTemplateRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateJpaRepository
        extends JpaRepository<NotificationTemplate, String>, NotificationTemplateRepository {

    @Override
    default List<NotificationTemplate> findAll() {
        return findAll(Sort.by(Sort.Direction.ASC, "templateCode"));
    }

    @Override
    default Optional<NotificationTemplate> findByCode(String templateCode) {
        return findById(templateCode);
    }
}
