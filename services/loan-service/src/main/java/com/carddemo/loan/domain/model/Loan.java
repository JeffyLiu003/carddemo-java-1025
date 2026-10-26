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

/**
 * 贷款聚合根（LOAN）。审批通过时按申请条款建账，放款后开始计息还款，最后一期入账后 CLOSED。
 */
@Entity
@Table(name = "loan")
public class Loan {

    @Id
    @Column(name = "loan_id", length = 36, nullable = false, updatable = false)
    private String loanId;

    @Column(name = "application_id", length = 36, nullable = false, updatable = false)
    private String applicationId;

    @Column(name = "customer_id", length = 20, nullable = false, updatable = false)
    private String customerId;

    @Column(name = "principal", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal principal;

    @Column(name = "outstanding_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Column(name = "term_months", nullable = false, updatable = false)
    private int termMonths;

    @Column(name = "annual_rate", nullable = false, precision = 6, scale = 4, updatable = false)
    private BigDecimal annualRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private LoanStatus status;

    @Column(name = "disbursed_at")
    private LocalDateTime disbursedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Loan() {
        // for JPA
    }

    public static Loan originate(String loanId, LoanApplication application) {
        Loan loan = new Loan();
        loan.loanId = loanId;
        loan.applicationId = application.getApplicationId();
        loan.customerId = application.getCustomerId();
        loan.principal = application.getPrincipal();
        loan.outstandingPrincipal = application.getPrincipal();
        loan.termMonths = application.getTermMonths();
        loan.annualRate = application.getAnnualRate();
        loan.status = LoanStatus.ACTIVE;
        return loan;
    }

    /**
     * 放款：记录放款时刻，此后才允许还款。
     */
    public void disburse(LocalDateTime at) {
        if (status != LoanStatus.ACTIVE) {
            throw new BusinessRuleException("LOAN_NOT_ACTIVE",
                    "Loan " + loanId + " is " + status + " and cannot be disbursed.");
        }
        if (disbursedAt != null) {
            throw new BusinessRuleException("LOAN_ALREADY_DISBURSED",
                    "Loan " + loanId + " was already disbursed at " + disbursedAt + ".");
        }
        this.disbursedAt = at == null ? LocalDateTime.now() : at;
    }

    public boolean isDisbursed() {
        return disbursedAt != null;
    }

    /**
     * 冲减本金：还款只冲抵本金部分，利息已在期次 total_due 中一并结清。
     */
    public void applyPrincipalPayment(BigDecimal principalAmount) {
        if (principalAmount == null || principalAmount.signum() < 0) {
            throw new BusinessRuleException("INVALID_REPAYMENT_AMOUNT",
                    "Repaid principal must not be negative.");
        }
        BigDecimal remaining = outstandingPrincipal.subtract(principalAmount);
        if (remaining.signum() < 0) {
            throw new BusinessRuleException("REPAYMENT_EXCEEDS_BALANCE",
                    "Repayment exceeds outstanding principal " + outstandingPrincipal + ".");
        }
        this.outstandingPrincipal = remaining.setScale(2);
    }

    /**
     * 结清：所有期次 PAID 后调用。
     */
    public void close() {
        if (status != LoanStatus.ACTIVE) {
            throw new BusinessRuleException("LOAN_NOT_ACTIVE",
                    "Loan " + loanId + " is " + status + " and cannot be closed.");
        }
        this.status = LoanStatus.CLOSED;
        this.outstandingPrincipal = BigDecimal.ZERO.setScale(2);
    }

    public String getLoanId() {
        return loanId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getOutstandingPrincipal() {
        return outstandingPrincipal;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public LocalDateTime getDisbursedAt() {
        return disbursedAt;
    }

    public long getVersion() {
        return version;
    }
}
