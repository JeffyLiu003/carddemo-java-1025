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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 还款计划期次（REPAYMENT_SCHEDULE）。等额本息，最后一期本金取剩余本金。
 */
@Entity
@Table(name = "repayment_schedule")
public class RepaymentSchedule {

    @Id
    @Column(name = "installment_id", length = 36, nullable = false, updatable = false)
    private String installmentId;

    @Column(name = "loan_id", length = 36, nullable = false, updatable = false)
    private String loanId;

    @Column(name = "installment_no", nullable = false, updatable = false)
    private int installmentNo;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal interestDue;

    @Column(name = "total_due", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private InstallmentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RepaymentSchedule() {
        // for JPA
    }

    public static RepaymentSchedule installment(String installmentId, String loanId, int installmentNo,
                                                LocalDate dueDate, BigDecimal principalDue,
                                                BigDecimal interestDue) {
        RepaymentSchedule schedule = new RepaymentSchedule();
        schedule.installmentId = installmentId;
        schedule.loanId = loanId;
        schedule.installmentNo = installmentNo;
        schedule.dueDate = dueDate;
        schedule.principalDue = principalDue.setScale(2);
        schedule.interestDue = interestDue.setScale(2);
        schedule.totalDue = schedule.principalDue.add(schedule.interestDue);
        schedule.status = InstallmentStatus.PENDING;
        return schedule;
    }

    public void markPaid(LocalDateTime at) {
        if (status == InstallmentStatus.PAID) {
            throw new BusinessRuleException("INSTALLMENT_ALREADY_PAID",
                    "Installment " + installmentNo + " of loan " + loanId + " is already paid.");
        }
        this.status = InstallmentStatus.PAID;
        this.paidAt = at == null ? LocalDateTime.now() : at;
    }

    public String getInstallmentId() {
        return installmentId;
    }

    public String getLoanId() {
        return loanId;
    }

    public int getInstallmentNo() {
        return installmentNo;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getPrincipalDue() {
        return principalDue;
    }

    public BigDecimal getInterestDue() {
        return interestDue;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }

    public InstallmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public long getVersion() {
        return version;
    }
}
