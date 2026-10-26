package com.carddemo.dispute.domain.model;

import com.carddemo.dispute.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 争议案件聚合根（DISPUTE_CASE）。状态机（设计 §5.2）：
 * <pre>
 * OPEN --review--> UNDER_REVIEW --provisional-credit--> PROVISIONAL_CREDIT
 *      --chargeback--> CHARGEBACK --resolve(won)--> WON / LOST --resolve--> CLOSED
 * </pre>
 * 非法迁移一律抛 {@link BusinessRuleException}（409）。
 */
@Entity
@Table(name = "dispute_case")
public class DisputeCase {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    @Id
    @Column(name = "case_id", length = 36, nullable = false, updatable = false)
    private String caseId;

    @Column(name = "transaction_id", length = 36, nullable = false, updatable = false)
    private String transactionId;

    @Column(name = "account_id", length = 11, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "customer_id", length = 20, nullable = false, updatable = false)
    private String customerId;

    @Column(name = "reason_code", length = 30, nullable = false, updatable = false)
    private String reasonCode;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private DisputeStatus status;

    @Column(name = "provisional_credit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal provisionalCreditAmount;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DisputeCase() {
        // for JPA
    }

    public static DisputeCase open(String caseId, String transactionId, String accountId, String customerId,
                                   String reasonCode, BigDecimal amount, LocalDateTime openedAt) {
        DisputeCase disputeCase = new DisputeCase();
        disputeCase.caseId = requireText(caseId, "INVALID_CASE_ID", "Case id must be supplied.");
        disputeCase.transactionId = requireText(transactionId, "INVALID_TRANSACTION_ID",
                "Transaction id must be supplied.");
        disputeCase.accountId = requireText(accountId, "INVALID_ACCOUNT_ID", "Account id must be supplied.");
        disputeCase.customerId = requireText(customerId, "INVALID_CUSTOMER_ID", "Customer id must be supplied.");
        disputeCase.reasonCode = requireText(reasonCode, "INVALID_REASON_CODE",
                "Reason code must be supplied.");
        disputeCase.amount = requireAmount(amount);
        disputeCase.status = DisputeStatus.OPEN;
        disputeCase.provisionalCreditAmount = BigDecimal.ZERO.setScale(2);
        disputeCase.openedAt = openedAt == null ? LocalDateTime.now() : openedAt;
        disputeCase.updatedAt = disputeCase.openedAt;
        return disputeCase;
    }

    /**
     * 受理审核：OPEN → UNDER_REVIEW。
     */
    public void review(LocalDateTime at) {
        if (status != DisputeStatus.OPEN) {
            throw new BusinessRuleException("DISPUTE_NOT_OPEN",
                    "Dispute " + caseId + " can only be reviewed from OPEN (current " + status + ").");
        }
        apply(DisputeStatus.UNDER_REVIEW, at);
    }

    /**
     * 临时入账：UNDER_REVIEW → PROVISIONAL_CREDIT，金额必须大于 0 且不超过争议金额。
     */
    public void grantProvisionalCredit(BigDecimal creditAmount, LocalDateTime at) {
        if (status != DisputeStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("DISPUTE_NOT_UNDER_REVIEW",
                    "Dispute " + caseId + " can only receive provisional credit from UNDER_REVIEW (current "
                            + status + ").");
        }
        if (creditAmount == null || creditAmount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_PROVISIONAL_CREDIT_AMOUNT",
                    "Provisional credit amount must be greater than zero.");
        }
        if (creditAmount.scale() > 2) {
            throw new BusinessRuleException("INVALID_PROVISIONAL_CREDIT_AMOUNT",
                    "Provisional credit amount must have at most 2 decimal places.");
        }
        if (creditAmount.compareTo(amount) > 0) {
            throw new BusinessRuleException("PROVISIONAL_CREDIT_EXCEEDS_DISPUTE_AMOUNT",
                    "Provisional credit " + creditAmount + " exceeds disputed amount " + amount + ".");
        }
        this.provisionalCreditAmount = creditAmount.setScale(2);
        apply(DisputeStatus.PROVISIONAL_CREDIT, at);
    }

    /**
     * 发起 chargeback：PROVISIONAL_CREDIT → CHARGEBACK。
     */
    public void chargeback(LocalDateTime at) {
        if (status != DisputeStatus.PROVISIONAL_CREDIT) {
            throw new BusinessRuleException("DISPUTE_NOT_PROVISIONAL_CREDIT",
                    "Dispute " + caseId + " can only be charged back from PROVISIONAL_CREDIT (current "
                            + status + ").");
        }
        apply(DisputeStatus.CHARGEBACK, at);
    }

    /**
     * 结案两步：CHARGEBACK → WON / LOST（裁决），WON / LOST → CLOSED（归档）。
     * 设计 §5.2 未提供独立的 close 端点，故由第二次 resolve 完成归档。
     */
    public void resolve(boolean won, LocalDateTime at) {
        if (status == DisputeStatus.CHARGEBACK) {
            apply(won ? DisputeStatus.WON : DisputeStatus.LOST, at);
            this.closedAt = updatedAt;
            return;
        }
        if (status == DisputeStatus.WON || status == DisputeStatus.LOST) {
            apply(DisputeStatus.CLOSED, at);
            return;
        }
        throw new BusinessRuleException("DISPUTE_NOT_CHARGEBACK",
                "Dispute " + caseId + " can only be resolved from CHARGEBACK (current " + status + ").");
    }

    private void apply(DisputeStatus next, LocalDateTime at) {
        this.status = next;
        this.updatedAt = at == null ? LocalDateTime.now() : at;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_DISPUTE_AMOUNT",
                    "Disputed amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new BusinessRuleException("INVALID_DISPUTE_AMOUNT",
                    "Disputed amount must have at most 2 decimal places.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("INVALID_DISPUTE_AMOUNT", "Disputed amount is out of range.");
        }
        return amount.setScale(2);
    }

    private static String requireText(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(code, message);
        }
        return value.trim();
    }

    public String getCaseId() {
        return caseId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public BigDecimal getProvisionalCreditAmount() {
        return provisionalCreditAmount;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public long getVersion() {
        return version;
    }
}
