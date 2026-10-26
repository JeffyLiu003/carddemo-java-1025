package com.carddemo.fx.interfaces.rest.dto;

import com.carddemo.fx.domain.model.FxConversion;
import com.carddemo.fx.domain.repository.PageResult;

import java.util.List;

public record FxConversionListResponse(
        List<FxConversionResponse> items,
        int page,
        int size,
        long total) {

    public static FxConversionListResponse from(PageResult<FxConversion> result) {
        return new FxConversionListResponse(
                result.items().stream().map(FxConversionResponse::from).toList(),
                result.page(),
                result.size(),
                result.total());
    }
}
