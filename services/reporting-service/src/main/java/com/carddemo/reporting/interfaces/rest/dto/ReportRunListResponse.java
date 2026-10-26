package com.carddemo.reporting.interfaces.rest.dto;

import com.carddemo.reporting.domain.model.ReportRun;
import com.carddemo.reporting.domain.repository.PageResult;

import java.util.List;

public record ReportRunListResponse(List<ReportRunSummaryResponse> items,
                                    int page,
                                    int size,
                                    long total) {

    public static ReportRunListResponse from(PageResult<ReportRun> result, int page, int size) {
        return new ReportRunListResponse(
                result.items().stream().map(ReportRunSummaryResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
