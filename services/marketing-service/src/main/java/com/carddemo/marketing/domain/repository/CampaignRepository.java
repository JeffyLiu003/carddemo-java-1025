package com.carddemo.marketing.domain.repository;

import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.CampaignStatus;

import java.util.List;
import java.util.Optional;

/**
 * 活动仓储端口。
 */
public interface CampaignRepository {

    Campaign save(Campaign campaign);

    Optional<Campaign> findById(String campaignId);

    Optional<Campaign> findByCode(String code);

    List<Campaign> findByStatus(CampaignStatus status);

    PageResult<Campaign> search(CampaignStatus status, int page, int size);

    long countByStatus(CampaignStatus status);
}
