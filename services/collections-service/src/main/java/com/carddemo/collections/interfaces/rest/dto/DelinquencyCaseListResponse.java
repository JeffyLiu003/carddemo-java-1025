package com.carddemo.collections.interfaces.rest.dto;

import com.carddemo.collections.domain.model.DelinquencyCase;
import com.carddemo.collections.domain.repository.PageResult;

import java.util.List;

public record DelinquencyCaseListResponse(
        List<DelinquencyCaseResponse> items,
        int page,
        int size,
        long total) {

    public static DelinquencyCaseListResponse from(PageResult<DelinquencyCase> result, int page, int size) {
        return new DelinquencyCaseListResponse(
                result.items().stream().map(DelinquencyCaseResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
