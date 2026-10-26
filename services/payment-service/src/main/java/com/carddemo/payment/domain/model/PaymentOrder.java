package com.carddemo.payment.domain.model;

import com.carddemo.payment.domain.exception.BusinessRuleException;
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
 * 支付指令聚合根（PAYMENT_ORDER）。状态机（设计 §2.2）：
 * <pre>
 * RECEIVED --screen--> SCREENED --accept--> ACCEPTED --settle--> SETTLED
 *    |                    |                    |                   |
 *    +--reject--> REJECTED +----return----------+---return----------+--> RETURNED
 * </pre>
 * 非法迁移一律抛 {@link BusinessRuleException}；重复动作的幂等由应用服务处理。
 */
@Entity
@Table(name = "payment_order")
public class PaymentOrder {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    @Id
    @Column(name = "payment_id", length = 36, nullable = false, updatable = false)
    private String paymentId;

    @Column(name = "idempotency_key", length = 80, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "debtor_account_id", length = 11, nullable = false, updatable = false)
    private String debtorAccountId;

    @Column(name = "creditor_name", length = 100)
    private String creditorName;

    @Column(name = "creditor_account_number", length = 34)
    private String creditorAccountNumber;

    @Column(name = "creditor_bank_code", length = 11)
    private String creditorBankCode;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 10, nullable = false, updatable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private PaymentStatus status;

    @Column(name = "reference", length = 60)
    private String reference;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "transaction_batch_id", length = 36)
    private String transactionBatchId;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PaymentOrder() {
        // for JPA
    }

    /**
     * 受理支付指令：状态 RECEIVED；金额、币种、类型在受理时即校验（设计 §2.2 规则）。
     */
    public static PaymentOrder receive(String paymentId, String idempotencyKey, String debtorAccountId,
                                       String creditorName, String creditorAccountNumber,
                                       String creditorBankCode, BigDecimal amount, String currency,
                                       PaymentType paymentType, String reference, String description,
                                       LocalDateTime receivedAt) {
        LocalDateTime now = receivedAt == null ? LocalDateTime.now() : receivedAt;
        PaymentOrder order = new PaymentOrder();
        order.paymentId = paymentId;
        order.idempotencyKey = idempotencyKey;
        order.debtorAccountId = debtorAccountId;
        order.creditorName = creditorName;
        order.creditorAccountNumber = creditorAccountNumber;
        order.creditorBankCode = creditorBankCode;
        order.amount = requireAmount(amount);
        order.currency = requireCurrency(currency);
        order.paymentType = paymentType;
        order.status = PaymentStatus.RECEIVED;
        order.reference = reference;
        order.description = description;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    /**
     * 自动筛查通过：RECEIVED → SCREENED。
     */
    public void screen(LocalDateTime screenedAt) {
        if (status != PaymentStatus.RECEIVED) {
            throw new BusinessRuleException("INVALID_PAYMENT_STATUS",
                    "Payment " + paymentId + " cannot be screened from status " + status + ".");
        }
        apply(PaymentStatus.SCREENED, screenedAt);
    }

    /**
     * 筛查/受理拒绝：RECEIVED → REJECTED（终态）。
     */
    public void reject(String reason, LocalDateTime rejectedAt) {
        if (status != PaymentStatus.RECEIVED) {
            throw new BusinessRuleException("INVALID_PAYMENT_STATUS",
                    "Payment " + paymentId + " cannot be rejected from status " + status + ".");
        }
        apply(PaymentStatus.REJECTED, rejectedAt);
        this.rejectReason = reason;
    }

    /**
     * 人工接受：SCREENED → ACCEPTED。
     */
    public void accept(LocalDateTime acceptedAt) {
        if (status != PaymentStatus.SCREENED) {
            throw new BusinessRuleException("PAYMENT_NOT_SCREENED",
                    "Payment " + paymentId + " can only be accepted from SCREENED (current " + status + ").");
        }
        apply(PaymentStatus.ACCEPTED, acceptedAt);
    }

    /**
     * 清算：ACCEPTED → SETTLED，并保存过账批次号。
     */
    public void settle(String transactionBatchId, LocalDateTime settledAt) {
        if (status != PaymentStatus.ACCEPTED) {
            throw new BusinessRuleException("PAYMENT_NOT_ACCEPTED",
                    "Payment " + paymentId + " can only be settled from ACCEPTED (current " + status + ").");
        }
        apply(PaymentStatus.SETTLED, settledAt);
        this.transactionBatchId = transactionBatchId;
        this.settledAt = updatedAt;
    }

    /**
     * 退汇：ACCEPTED / SETTLED → RETURNED（终态）。已清算的单据由应用服务先行调用冲正。
     */
    public void returnPayment(LocalDateTime returnedAt) {
        if (status != PaymentStatus.ACCEPTED && status != PaymentStatus.SETTLED) {
            throw new BusinessRuleException("PAYMENT_NOT_RETURNABLE",
                    "Payment " + paymentId + " can only be returned from ACCEPTED or SETTLED (current "
                            + status + ").");
        }
        apply(PaymentStatus.RETURNED, returnedAt);
        this.returnedAt = updatedAt;
    }

    private void apply(PaymentStatus next, LocalDateTime at) {
        this.status = next;
        this.updatedAt = at == null ? LocalDateTime.now() : at;
    }

    /**
     * 金额校验：> 0、最多 2 位小数、上限 99,999,999.99（INVALID_PAYMENT_AMOUNT）。
     */
    public static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_PAYMENT_AMOUNT",
                    "Payment amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new BusinessRuleException("INVALID_PAYMENT_AMOUNT",
                    "Payment amount must have at most 2 decimal places.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("INVALID_PAYMENT_AMOUNT", "Payment amount is out of range.");
        }
        return amount.setScale(2);
    }

    /**
     * 币种校验：3 位字母 ISO 代码，统一大写（INVALID_CURRENCY）。
     */
    public static String requireCurrency(String currency) {
        String normalized = currency == null ? null : currency.trim().toUpperCase();
        if (normalized == null || normalized.length() != 3 || !normalized.chars().allMatch(Character::isLetter)) {
            throw new BusinessRuleException("INVALID_CURRENCY", "Currency must be a 3 letter ISO code.");
        }
        return normalized;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDebtorAccountId() {
        return debtorAccountId;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public String getCreditorAccountNumber() {
        return creditorAccountNumber;
    }

    public String getCreditorBankCode() {
        return creditorBankCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public String getTransactionBatchId() {
        return transactionBatchId;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public long getVersion() {
        return version;
    }
}
