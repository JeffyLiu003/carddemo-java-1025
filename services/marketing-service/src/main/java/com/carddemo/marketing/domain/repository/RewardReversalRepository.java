package com.carddemo.marketing.domain.repository;

import com.carddemo.marketing.domain.model.RewardReversal;

import java.util.Optional;

/**
 * 退款与冲正受理仓储端口。唯一键 (reversalTransactionId, rewardId) 保证同一退款交易只受理一次。
 */
public interface RewardReversalRepository {

    RewardReversal save(RewardReversal reversal);

    Optional<RewardReversal> findByReversalTransactionIdAndRewardId(String reversalTransactionId, String rewardId);
}
