package com.carddemo.aml.interfaces.rest.dto;

import com.carddemo.aml.domain.model.AmlAlert;
import com.carddemo.aml.domain.repository.PageResult;

import java.util.List;

/**
 * 警报分页响应（GET /api/v1/aml-alerts），按 createdAt 倒序。
 */
public record AlertListResponse(List<AlertResponse> items, int page, int size, long total) {

    public static AlertListResponse from(PageResult<AmlAlert> result, int page, int size) {
        return new AlertListResponse(
                result.items().stream().map(AlertResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
