package com.carddemo.aml.interfaces.rest.dto;

import com.carddemo.aml.domain.model.AmlAlert;
import com.carddemo.aml.domain.model.AmlAlertStatus;

import java.time.LocalDateTime;

/**
 * 警报响应（列表项与处置结果共用）。
 */
public record AlertResponse(
        String alertId,
        String screeningId,
        String customerId,
        AmlAlertStatus status,
        String details,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt) {

    public static AlertResponse from(AmlAlert alert) {
        return new AlertResponse(
                alert.getAlertId(),
                alert.getScreeningId(),
                alert.getCustomerId(),
                alert.getStatus(),
                alert.getDetails(),
                alert.getCreatedAt(),
                alert.getResolvedAt());
    }
}
