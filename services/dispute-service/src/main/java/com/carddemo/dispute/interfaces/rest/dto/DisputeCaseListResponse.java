package com.carddemo.dispute.interfaces.rest.dto;

import com.carddemo.dispute.domain.model.DisputeCase;
import com.carddemo.dispute.domain.repository.PageResult;

import java.util.List;

public record DisputeCaseListResponse(List<DisputeCaseResponse> items, int page, int size, long total) {

    public static DisputeCaseListResponse from(PageResult<DisputeCase> result, int page, int size) {
        return new DisputeCaseListResponse(
                result.items().stream().map(DisputeCaseResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
