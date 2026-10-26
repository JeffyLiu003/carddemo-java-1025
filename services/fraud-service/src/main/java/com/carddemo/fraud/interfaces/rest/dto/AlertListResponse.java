package com.carddemo.fraud.interfaces.rest.dto;

import com.carddemo.fraud.domain.model.FraudAlert;
import com.carddemo.fraud.domain.repository.PageResult;

import java.util.List;

/**
 * 警报分页响应（GET /api/v1/fraud-alerts），按 createdAt 倒序。
 */
public record AlertListResponse(List<AlertResponse> items, int page, int size, long total) {

    public static AlertListResponse from(PageResult<FraudAlert> result, int page, int size) {
        return new AlertListResponse(
                result.items().stream().map(AlertResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
