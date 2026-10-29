package com.carddemo.marketing.domain.model;

import com.carddemo.marketing.domain.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 退款与冲正受理记录（设计 §4.4，PRD REQ-CB-07）。
 *
 * <p>唯一 (reversal_transaction_id, reward_id)：同一退款交易对同一奖励只受理一次。
 * 状态只表示受理结论，资金是否已扣减由后续执行器推进。
 */
@Entity
@Table(name = "reward_reversal")
public class RewardReversal {

    public static final String INVALID_REVERSAL = "INVALID_REVERSAL";
    private static final int AMOUNT_SCALE = 2;

    @Id
    @Column(name = "reversal_id", length = 36, nullable = false, updatable = false)
    private String reversalId;

    @Column(name = "campaign_id", length = 36, nullable = false, updatable = false)
    private String campaignId;

    @Column(name = "reward_id", length = 36, nullable = false, updatable = false)
    private String rewardId;

    @Column(name = "reversal_transaction_id", length = 64, nullable = false, updatable = false)
    private String reversalTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reversal_type", length = 16, nullable = false, updatable = false)
    private ReversalType reversalType;

    @Column(name = "reversal_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal reversalAmount;

    @Column(name = "clawback_amount", precision = 14, scale = 2)
    private BigDecimal clawbackAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RewardReversalStatus status;

    @Column(name = "reason", length = 64, nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RewardReversal() {
        // for JPA
    }

    public static RewardReversal accept(String reversalId, String campaignId, String rewardId,
                                       String reversalTransactionId, ReversalType reversalType,
                                       BigDecimal reversalAmount, ReversalDecision decision,
                                       LocalDateTime createdAt) {
        Objects.requireNonNull(decision, "decision must not be null");
        RewardReversal reversal = new RewardReversal();
        reversal.reversalId = requireText(reversalId, "reversalId");
        reversal.campaignId = requireText(campaignId, "campaignId");
        reversal.rewardId = requireText(rewardId, "rewardId");
        reversal.reversalTransactionId = requireText(reversalTransactionId, "reversalTransactionId");
        reversal.reversalType = Objects.requireNonNull(reversalType, "reversalType must not be null");
        reversal.reversalAmount = positiveAmount(reversalAmount);
        reversal.clawbackAmount = decision.clawbackAmount() == null
                ? null : decision.clawbackAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        reversal.status = Objects.requireNonNull(decision.status(), "status must not be null");
        reversal.reason = requireText(decision.reason(), "reason");
        reversal.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        return reversal;
    }

    private static BigDecimal positiveAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new InvalidRequestException("INVALID_AMOUNT", "Reversal amount must be greater than 0.");
        }
        return value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(INVALID_REVERSAL, "Reversal " + field + " must be supplied.");
        }
        return value.trim();
    }

    public String getReversalId() {
        return reversalId;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public String getRewardId() {
        return rewardId;
    }

    public String getReversalTransactionId() {
        return reversalTransactionId;
    }

    public ReversalType getReversalType() {
        return reversalType;
    }

    public BigDecimal getReversalAmount() {
        return reversalAmount;
    }

    public BigDecimal getClawbackAmount() {
        return clawbackAmount;
    }

    public RewardReversalStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
