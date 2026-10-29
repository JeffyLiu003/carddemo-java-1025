package com.carddemo.marketing.infrastructure.persistence;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.model.RewardReversal;
import com.carddemo.marketing.domain.repository.RewardReversalRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RewardReversalJpaRepository extends JpaRepository<RewardReversal, String>,
        RewardReversalRepository {

    @Override
    Optional<RewardReversal> findByReversalTransactionIdAndRewardId(String reversalTransactionId, String rewardId);

    /**
     * 唯一索引 uk_reward_reversal_transaction_reward 兜底并发重复受理；正常重复请求在服务层先行返回既有记录。
     */
    @Override
    default RewardReversal save(RewardReversal reversal) {
        try {
            return saveAndFlush(reversal);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("REVERSAL_ALREADY_RECORDED",
                    "Reversal " + reversal.getReversalTransactionId() + " for reward "
                            + reversal.getRewardId() + " is already recorded.");
        }
    }
}
