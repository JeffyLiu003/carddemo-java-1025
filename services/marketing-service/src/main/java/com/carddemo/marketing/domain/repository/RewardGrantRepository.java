package com.carddemo.marketing.domain.repository;

import com.carddemo.marketing.domain.model.RewardGrant;
import com.carddemo.marketing.domain.model.RewardGrantStatus;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 返现发放仓储端口。客户上限与预算口径都按 PENDING+GRANTED 累计（PC-09）。
 */
public interface RewardGrantRepository {

    RewardGrant save(RewardGrant grant);

    Optional<RewardGrant> findById(String rewardId);

    Optional<RewardGrant> findByCampaignIdAndTransactionId(String campaignId, String transactionId);

    PageResult<RewardGrant> search(String accountId, String customerId, int page, int size);

    /**
     * 客户在活动内的累计返现（PENDING+GRANTED）。
     */
    BigDecimal customerCashback(String campaignId, String customerId);

    /**
     * 活动已入账（GRANTED）的返现合计。
     */
    BigDecimal grantedCashback(String campaignId);

    /**
     * 全仓已入账（GRANTED）的返现合计。
     */
    BigDecimal totalGrantedCashback();

    long countByCampaignId(String campaignId);

    long countByCampaignIdAndStatus(String campaignId, RewardGrantStatus status);

    long countByStatus(RewardGrantStatus status);
}
