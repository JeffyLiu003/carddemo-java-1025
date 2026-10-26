package com.carddemo.fraud.interfaces.rest.dto;

import com.carddemo.fraud.domain.model.FraudAlert;
import com.carddemo.fraud.domain.model.FraudAlertStatus;

import java.time.LocalDateTime;

/**
 * 警报响应（列表项与详情共用）。
 */
public record AlertResponse(
        String alertId,
        String evaluationId,
        String accountId,
        int score,
        FraudAlertStatus status,
        String reason,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt) {

    public static AlertResponse from(FraudAlert alert) {
        return new AlertResponse(
                alert.getAlertId(),
                alert.getEvaluationId(),
                alert.getAccountId(),
                alert.getScore(),
                alert.getStatus(),
                alert.getReason(),
                alert.getCreatedAt(),
                alert.getResolvedAt());
    }
}
