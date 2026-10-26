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
 * 返现发放记录（设计 §3.1）。唯一 (campaign_id, transaction_id)：同一活动 + 同一交易只返现一次。
 *
 * <p>生命周期（PC-10）：评估时直接以 PENDING 落库并预占预算；贷记成功 → GRANTED + grantedAt；
 * 贷记失败 → 保留 PENDING 并记录 failureReason，等待重试；重试幂等，成功后不再重复贷记。
 */
@Entity
@Table(name = "reward_grant")
public class RewardGrant {

    public static final String INVALID_GRANT = "INVALID_REWARD_GRANT";
    private static final int AMOUNT_SCALE = 2;
    private static final int REASON_MAX_LENGTH = 500;

    @Id
    @Column(name = "reward_id", length = 36, nullable = false, updatable = false)
    private String rewardId;

    @Column(name = "campaign_id", length = 36, nullable = false, updatable = false)
    private String campaignId;

    @Column(name = "transaction_id", length = 64, nullable = false, updatable = false)
    private String transactionId;

    @Column(name = "account_id", length = 11, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "card_number", length = 16, updatable = false)
    private String cardNumber;

    @Column(name = "customer_id", length = 9, nullable = false, updatable = false)
    private String customerId;

    @Column(name = "transaction_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal transactionAmount;

    @Column(name = "cashback_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal cashbackAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RewardGrantStatus status;

    @Column(name = "failure_reason", length = REASON_MAX_LENGTH)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RewardGrant() {
        // for JPA
    }

    public static RewardGrant pending(String rewardId, String campaignId, String transactionId, String accountId,
                                      String cardNumber, String customerId, BigDecimal transactionAmount,
                                      BigDecimal cashbackAmount, LocalDateTime createdAt) {
        RewardGrant grant = new RewardGrant();
        grant.rewardId = requireText(rewardId, "rewardId");
        grant.campaignId = requireText(campaignId, "campaignId");
        grant.transactionId = requireText(transactionId, "transactionId");
        grant.accountId = requireText(accountId, "accountId");
        grant.cardNumber = blankToNull(cardNumber);
        grant.customerId = requireText(customerId, "customerId");
        grant.transactionAmount = amount(transactionAmount);
        grant.cashbackAmount = amount(cashbackAmount);
        if (grant.cashbackAmount.signum() <= 0) {
            throw new InvalidRequestException(INVALID_GRANT, "Reward cashbackAmount must be greater than 0.");
        }
        grant.status = RewardGrantStatus.PENDING;
        grant.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        return grant;
    }

    /**
     * 贷记成功：置 GRANTED 并记录入账时间；重复调用保留首次入账时间（幂等）。
     */
    public void markGranted(LocalDateTime grantedAt) {
        Objects.requireNonNull(grantedAt, "grantedAt must not be null");
        if (status == RewardGrantStatus.GRANTED) {
            return;
        }
        status = RewardGrantStatus.GRANTED;
        this.grantedAt = grantedAt;
        failureReason = null;
    }

    /**
     * 贷记失败：保留 PENDING 供重试，只记录失败原因。
     */
    public void recordFailure(String reason) {
        status = RewardGrantStatus.PENDING;
        failureReason = truncate(reason);
    }

    public boolean isRetryable() {
        return status.isRetryable();
    }

    private static BigDecimal amount(BigDecimal value) {
        if (value == null) {
            throw new InvalidRequestException(INVALID_GRANT, "Reward amount must be supplied.");
        }
        return value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(INVALID_GRANT, "Reward " + field + " must be supplied.");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > REASON_MAX_LENGTH ? message.substring(0, REASON_MAX_LENGTH) : message;
    }

    public String getRewardId() {
        return rewardId;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public BigDecimal getCashbackAmount() {
        return cashbackAmount;
    }

    public RewardGrantStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public long getVersion() {
        return version;
    }
}
