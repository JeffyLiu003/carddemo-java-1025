package com.carddemo.loan.domain.model;

import com.carddemo.loan.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 贷款申请聚合根（LOAN_APPLICATION）。状态机（设计 §2.1、§2.2）：
 * <pre>
 * SUBMITTED --approve--> APPROVED --disburse--> DISBURSED --repay(all)--> CLOSED
 *     |
 *     +--reject--> REJECTED（终态）
 * </pre>
 */
@Entity
@Table(name = "loan_application")
public class LoanApplication {

    private static final BigDecimal MAX_PRINCIPAL = new BigDecimal("99999999.99");
    private static final BigDecimal MAX_ANNUAL_RATE = new BigDecimal("1.0000");
    private static final int MAX_TERM_MONTHS = 360;

    @Id
    @Column(name = "application_id", length = 36, nullable = false, updatable = false)
    private String applicationId;

    @Column(name = "customer_id", length = 20, nullable = false, updatable = false)
    private String customerId;

    @Column(name = "product_code", length = 20, nullable = false, updatable = false)
    private String productCode;

    @Column(name = "principal", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal principal;

    @Column(name = "term_months", nullable = false, updatable = false)
    private int termMonths;

    @Column(name = "annual_rate", nullable = false, precision = 6, scale = 4, updatable = false)
    private BigDecimal annualRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private LoanApplicationStatus status;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected LoanApplication() {
        // for JPA
    }

    public static LoanApplication submit(String applicationId, String customerId, String productCode,
                                         BigDecimal principal, int termMonths, BigDecimal annualRate,
                                         LocalDateTime createdAt) {
        LoanApplication application = new LoanApplication();
        application.applicationId = requireText(applicationId, "INVALID_APPLICATION_ID",
                "Application id must be supplied.");
        application.customerId = requireText(customerId, "INVALID_CUSTOMER_ID",
                "Customer id must be supplied.");
        application.productCode = requireText(productCode, "INVALID_PRODUCT_CODE",
                "Product code must be supplied.").toUpperCase(Locale.ROOT);
        application.principal = requirePrincipal(principal);
        application.termMonths = requireTermMonths(termMonths);
        application.annualRate = requireAnnualRate(annualRate);
        application.status = LoanApplicationStatus.SUBMITTED;
        application.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        return application;
    }

    /**
     * 审批通过：SUBMITTED → APPROVED。还款计划由应用层按申请条款生成。
     */
    public void approve(LocalDateTime decidedAt) {
        if (status != LoanApplicationStatus.SUBMITTED) {
            throw new BusinessRuleException("APPLICATION_NOT_SUBMITTED",
                    "Application " + applicationId + " can only be approved from SUBMITTED (current "
                            + status + ").");
        }
        this.status = LoanApplicationStatus.APPROVED;
        this.decidedAt = decidedAt == null ? LocalDateTime.now() : decidedAt;
    }

    /**
     * 拒绝：SUBMITTED → REJECTED（终态）。
     */
    public void reject(String reason, LocalDateTime decidedAt) {
        if (status != LoanApplicationStatus.SUBMITTED) {
            throw new BusinessRuleException("APPLICATION_NOT_SUBMITTED",
                    "Application " + applicationId + " can only be rejected from SUBMITTED (current "
                            + status + ").");
        }
        this.status = LoanApplicationStatus.REJECTED;
        this.rejectReason = requireText(reason, "INVALID_REJECT_REASON",
                "Reject reason must be supplied.");
        this.decidedAt = decidedAt == null ? LocalDateTime.now() : decidedAt;
    }

    /**
     * 放款：APPROVED → DISBURSED。放款时刻记录在 loan.disbursed_at，申请本身只保留决策时间。
     */
    public void markDisbursed() {
        if (status != LoanApplicationStatus.APPROVED) {
            throw new BusinessRuleException("APPLICATION_NOT_APPROVED",
                    "Application " + applicationId + " can only be disbursed from APPROVED (current "
                            + status + ").");
        }
        this.status = LoanApplicationStatus.DISBURSED;
    }

    /**
     * 结清：DISBURSED → CLOSED，由还款流程在最后一期入账后调用。
     */
    public void close() {
        if (status != LoanApplicationStatus.DISBURSED) {
            throw new BusinessRuleException("APPLICATION_NOT_DISBURSED",
                    "Application " + applicationId + " can only be closed from DISBURSED (current "
                            + status + ").");
        }
        this.status = LoanApplicationStatus.CLOSED;
    }

    private static BigDecimal requirePrincipal(BigDecimal principal) {
        if (principal == null || principal.signum() <= 0) {
            throw new BusinessRuleException("INVALID_LOAN_PRINCIPAL",
                    "Loan principal must be greater than zero.");
        }
        if (principal.scale() > 2) {
            throw new BusinessRuleException("INVALID_LOAN_PRINCIPAL",
                    "Loan principal must have at most 2 decimal places.");
        }
        if (principal.compareTo(MAX_PRINCIPAL) > 0) {
            throw new BusinessRuleException("INVALID_LOAN_PRINCIPAL", "Loan principal is out of range.");
        }
        return principal.setScale(2);
    }

    private static int requireTermMonths(int termMonths) {
        if (termMonths < 1 || termMonths > MAX_TERM_MONTHS) {
            throw new BusinessRuleException("INVALID_LOAN_TERM",
                    "Loan term must be between 1 and " + MAX_TERM_MONTHS + " months.");
        }
        return termMonths;
    }

    private static BigDecimal requireAnnualRate(BigDecimal annualRate) {
        if (annualRate == null || annualRate.signum() < 0) {
            throw new BusinessRuleException("INVALID_ANNUAL_RATE",
                    "Annual rate must not be negative.");
        }
        if (annualRate.compareTo(MAX_ANNUAL_RATE) >= 0) {
            throw new BusinessRuleException("INVALID_ANNUAL_RATE",
                    "Annual rate must be lower than 100%.");
        }
        return annualRate.setScale(4);
    }

    private static String requireText(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(code, message);
        }
        return value.trim();
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public LoanApplicationStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public long getVersion() {
        return version;
    }
}
