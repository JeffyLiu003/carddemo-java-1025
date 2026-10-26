package com.carddemo.fx.interfaces.rest.dto;

import com.carddemo.fx.domain.model.FxRate;
import com.carddemo.fx.domain.repository.PageResult;

import java.util.List;

public record FxRateListResponse(
        List<FxRateResponse> items,
        int page,
        int size,
        long total) {

    public static FxRateListResponse from(PageResult<FxRate> result) {
        return new FxRateListResponse(
                result.items().stream().map(FxRateResponse::from).toList(),
                result.page(),
                result.size(),
                result.total());
    }
}
