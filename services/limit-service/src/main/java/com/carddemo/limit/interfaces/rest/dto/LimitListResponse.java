package com.carddemo.limit.interfaces.rest.dto;

import com.carddemo.limit.domain.model.LimitDefinition;
import com.carddemo.limit.domain.repository.PageResult;

import java.util.List;

public record LimitListResponse(
        List<LimitResponse> items,
        int page,
        int size,
        long total) {

    public static LimitListResponse from(PageResult<LimitDefinition> result) {
        return new LimitListResponse(
                result.items().stream().map(LimitResponse::from).toList(),
                result.page(),
                result.size(),
                result.total());
    }
}
