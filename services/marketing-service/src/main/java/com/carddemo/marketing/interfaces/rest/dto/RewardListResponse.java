package com.carddemo.marketing.interfaces.rest.dto;

import com.carddemo.marketing.application.RewardView;
import com.carddemo.marketing.domain.repository.PageResult;

import java.util.List;

public record RewardListResponse(
        List<RewardView> items,
        int page,
        int size,
        long total) {

    public static RewardListResponse from(PageResult<RewardView> result, int page, int size) {
        return new RewardListResponse(result.items(), page, size, result.total());
    }
}
