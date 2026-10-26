package com.carddemo.loan.interfaces.rest.dto;

import com.carddemo.loan.domain.model.LoanApplication;
import com.carddemo.loan.domain.repository.PageResult;

import java.util.List;

public record LoanApplicationListResponse(List<LoanApplicationResponse> items, int page, int size, long total) {

    public static LoanApplicationListResponse from(PageResult<LoanApplication> result, int page, int size) {
        return new LoanApplicationListResponse(
                result.items().stream().map(LoanApplicationResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
