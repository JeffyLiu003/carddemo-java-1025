package com.carddemo.marketing.interfaces.rest.dto;

import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.repository.PageResult;

import java.util.List;

public record CampaignListResponse(
        List<CampaignResponse> items,
        int page,
        int size,
        long total) {

    public static CampaignListResponse from(PageResult<Campaign> result, int page, int size) {
        return new CampaignListResponse(
                result.items().stream().map(CampaignResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
