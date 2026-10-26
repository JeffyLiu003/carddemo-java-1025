package com.carddemo.fraud.domain.repository;

import com.carddemo.fraud.domain.model.FraudAlert;
import com.carddemo.fraud.domain.model.FraudAlertStatus;

import java.util.Optional;

/**
 * 欺诈警报仓储端口（fraud_alert）。列表按 createdAt 倒序。
 */
public interface FraudAlertRepository {

    FraudAlert saveAlert(FraudAlert alert);

    Optional<FraudAlert> findAlert(String alertId);

    Optional<FraudAlert> findAlertByEvaluation(String evaluationId);

    PageResult<FraudAlert> findAlerts(FraudAlertStatus status, String accountId, int page, int size);
}
