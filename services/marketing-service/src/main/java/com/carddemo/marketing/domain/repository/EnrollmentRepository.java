package com.carddemo.marketing.domain.repository;

import com.carddemo.marketing.domain.model.Enrollment;

import java.util.Optional;

/**
 * 报名仓储端口。唯一 (campaign_id, account_id) 由数据库唯一索引兜底。
 */
public interface EnrollmentRepository {

    Enrollment save(Enrollment enrollment);

    Optional<Enrollment> findByCampaignIdAndAccountId(String campaignId, String accountId);

    boolean existsByCampaignIdAndAccountId(String campaignId, String accountId);

    boolean existsByCampaignIdAndCustomerId(String campaignId, String customerId);

    boolean existsByCampaignIdAndCardNumber(String campaignId, String cardNumber);

    PageResult<Enrollment> search(String campaignId, int page, int size);

    long countByCampaignId(String campaignId);
}
