package com.carddemo.billing.interfaces.rest.dto;

import com.carddemo.billing.domain.model.Statement;
import com.carddemo.billing.domain.repository.PageResult;

import java.util.List;

public record StatementListResponse(
        List<StatementResponse> items,
        int page,
        int size,
        long total) {

    public static StatementListResponse from(PageResult<Statement> result, int page, int size) {
        return new StatementListResponse(
                result.items().stream().map(StatementResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
