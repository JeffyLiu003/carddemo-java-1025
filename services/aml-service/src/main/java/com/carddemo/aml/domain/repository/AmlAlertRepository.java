package com.carddemo.aml.domain.repository;

import com.carddemo.aml.domain.model.AmlAlert;
import com.carddemo.aml.domain.model.AmlAlertStatus;

import java.util.Optional;

/**
 * AML 警报仓储端口（aml_alert）。列表按 createdAt 倒序。
 */
public interface AmlAlertRepository {

    AmlAlert saveAlert(AmlAlert alert);

    Optional<AmlAlert> findAlert(String alertId);

    Optional<AmlAlert> findAlertByScreening(String screeningId);

    PageResult<AmlAlert> findAlerts(AmlAlertStatus status, int page, int size);
}
