package com.carddemo.aml.infrastructure.persistence;

import com.carddemo.aml.domain.model.AmlAlert;
import com.carddemo.aml.domain.model.AmlAlertStatus;
import com.carddemo.aml.domain.repository.AmlAlertRepository;
import com.carddemo.aml.domain.repository.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AmlAlertJpaRepository
        extends JpaRepository<AmlAlert, String>, AmlAlertRepository {

    @Override
    default AmlAlert saveAlert(AmlAlert alert) {
        return save(alert);
    }

    @Override
    default Optional<AmlAlert> findAlert(String alertId) {
        return findById(alertId);
    }

    @Override
    default Optional<AmlAlert> findAlertByScreening(String screeningId) {
        return findFirstByScreeningIdOrderByCreatedAtDesc(screeningId);
    }

    @Override
    default PageResult<AmlAlert> findAlerts(AmlAlertStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("alertId")));
        Page<AmlAlert> result = status == null ? findAll(pageable) : findAllByStatus(status, pageable);
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    Page<AmlAlert> findAllByStatus(AmlAlertStatus status, Pageable pageable);

    Optional<AmlAlert> findFirstByScreeningIdOrderByCreatedAtDesc(String screeningId);
}
