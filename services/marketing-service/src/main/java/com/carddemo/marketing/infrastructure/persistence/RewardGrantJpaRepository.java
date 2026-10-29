package com.carddemo.marketing.infrastructure.persistence;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.model.RewardGrant;
import com.carddemo.marketing.domain.model.RewardGrantStatus;
import com.carddemo.marketing.domain.repository.PageResult;
import com.carddemo.marketing.domain.repository.RewardGrantRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RewardGrantJpaRepository extends JpaRepository<RewardGrant, String>, RewardGrantRepository {

    @Override
    Optional<RewardGrant> findByCampaignIdAndTransactionId(String campaignId, String transactionId);

    @Override
    List<RewardGrant> findByTransactionId(String transactionId);

    Page<RewardGrant> findByAccountId(String accountId, Pageable pageable);

    Page<RewardGrant> findByCustomerId(String customerId, Pageable pageable);

    Page<RewardGrant> findByAccountIdAndCustomerId(String accountId, String customerId, Pageable pageable);

    /**
     * 唯一索引 uk_reward_grant_campaign_transaction 兜底并发重复评估。
     */
    @Override
    default RewardGrant save(RewardGrant grant) {
        try {
            return saveAndFlush(grant);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("REWARD_ALREADY_EVALUATED",
                    "Transaction " + grant.getTransactionId() + " was already evaluated for campaign "
                            + grant.getCampaignId() + ".");
        }
    }

    @Override
    default PageResult<RewardGrant> search(String accountId, String customerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "rewardId")));
        Page<RewardGrant> result;
        if (StringUtils.hasText(accountId) && StringUtils.hasText(customerId)) {
            result = findByAccountIdAndCustomerId(accountId, customerId, pageable);
        } else if (StringUtils.hasText(accountId)) {
            result = findByAccountId(accountId, pageable);
        } else if (StringUtils.hasText(customerId)) {
            result = findByCustomerId(customerId, pageable);
        } else {
            result = findAll(pageable);
        }
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    @Override
    default BigDecimal customerCashback(String campaignId, String customerId) {
        return sumCashbackByCustomer(campaignId, customerId,
                List.of(RewardGrantStatus.PENDING, RewardGrantStatus.GRANTED));
    }

    @Override
    default BigDecimal grantedCashback(String campaignId) {
        return sumCashbackByCampaignAndStatus(campaignId, RewardGrantStatus.GRANTED);
    }

    @Override
    default BigDecimal totalGrantedCashback() {
        return sumCashbackByStatus(RewardGrantStatus.GRANTED);
    }

    @Query("""
            select coalesce(sum(g.cashbackAmount), 0)
            from RewardGrant g
            where g.campaignId = :campaignId
              and g.customerId = :customerId
              and g.status in :statuses
            """)
    BigDecimal sumCashbackByCustomer(@Param("campaignId") String campaignId,
                                     @Param("customerId") String customerId,
                                     @Param("statuses") List<RewardGrantStatus> statuses);

    @Query("""
            select coalesce(sum(g.cashbackAmount), 0)
            from RewardGrant g
            where g.campaignId = :campaignId
              and g.status = :status
            """)
    BigDecimal sumCashbackByCampaignAndStatus(@Param("campaignId") String campaignId,
                                              @Param("status") RewardGrantStatus status);

    @Query("""
            select coalesce(sum(g.cashbackAmount), 0)
            from RewardGrant g
            where g.status = :status
            """)
    BigDecimal sumCashbackByStatus(@Param("status") RewardGrantStatus status);
}
