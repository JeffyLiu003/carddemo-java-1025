package com.carddemo.reconciliation.interfaces.rest.dto;

import com.carddemo.reconciliation.domain.model.EodRun;
import com.carddemo.reconciliation.domain.repository.PageResult;

import java.util.List;

public record EodRunListResponse(
        List<EodRunResponse> items,
        int page,
        int size,
        long total) {

    public static EodRunListResponse from(PageResult<EodRun> result, int page, int size) {
        return new EodRunListResponse(
                result.items().stream().map(EodRunResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
