package com.carddemo.reconciliation.interfaces.rest.dto;

import com.carddemo.reconciliation.domain.model.ReconciliationRun;
import com.carddemo.reconciliation.domain.repository.PageResult;

import java.util.List;

public record ReconciliationRunListResponse(
        List<ReconciliationRunSummaryResponse> items,
        int page,
        int size,
        long total) {

    public static ReconciliationRunListResponse from(PageResult<ReconciliationRun> result, int page, int size) {
        return new ReconciliationRunListResponse(
                result.items().stream().map(ReconciliationRunSummaryResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
