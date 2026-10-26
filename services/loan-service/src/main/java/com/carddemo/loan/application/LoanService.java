package com.carddemo.loan.application;

import com.carddemo.loan.domain.exception.BusinessRuleException;
import com.carddemo.loan.domain.exception.NotFoundException;
import com.carddemo.loan.domain.model.InstallmentStatus;
import com.carddemo.loan.domain.model.Loan;
import com.carddemo.loan.domain.model.LoanApplication;
import com.carddemo.loan.domain.model.LoanStatus;
import com.carddemo.loan.domain.model.RepaymentSchedule;
import com.carddemo.loan.domain.repository.LoanApplicationRepository;
import com.carddemo.loan.domain.repository.LoanRepository;
import com.carddemo.loan.domain.repository.RepaymentScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 贷款用例（设计 §2.2）：放款、详情、还款计划、还款冲抵。
 * <p>
 * 还款策略：本期本金部分冲减 outstanding_principal，利息随期次一并结清；
 * 单次还款金额必须等于下一待还期次的 total_due（逐期还款）或全部剩余期次总额（提前结清），
 * 其他金额一律 409，避免出现设计 §2.1 未定义的“部分已还”字段。
 */
@Service
@Transactional
public class LoanService {

    private final LoanRepository loans;
    private final LoanApplicationRepository applications;
    private final RepaymentScheduleRepository schedules;

    public LoanService(LoanRepository loans, LoanApplicationRepository applications,
                       RepaymentScheduleRepository schedules) {
        this.loans = loans;
        this.applications = applications;
        this.schedules = schedules;
    }

    public Loan disburse(String loanId) {
        Loan loan = requireLoan(loanId);
        LoanApplication application = requireApplication(loan.getApplicationId());
        loan.disburse(LocalDateTime.now());
        application.markDisbursed();
        loans.saveLoan(loan);
        applications.saveApplication(application);
        return loan;
    }

    @Transactional(readOnly = true)
    public Loan get(String loanId) {
        return requireLoan(loanId);
    }

    @Transactional(readOnly = true)
    public List<RepaymentSchedule> schedule(String loanId) {
        requireLoan(loanId);
        return schedules.findSchedulesByLoanId(loanId);
    }

    public RepaymentResult repay(String loanId, BigDecimal amount) {
        Loan loan = requireLoan(loanId);
        if (!loan.isDisbursed()) {
            throw new BusinessRuleException("LOAN_NOT_DISBURSED",
                    "Loan " + loanId + " has not been disbursed yet.");
        }
        if (loan.getStatus() == LoanStatus.CLOSED) {
            throw new BusinessRuleException("LOAN_ALREADY_CLOSED", "Loan " + loanId + " is already closed.");
        }

        List<RepaymentSchedule> pending = schedules.findSchedulesByLoanIdAndStatus(loanId,
                InstallmentStatus.PENDING);
        if (pending.isEmpty()) {
            throw new BusinessRuleException("NOTHING_TO_REPAY", "Loan " + loanId + " has no pending installment.");
        }
        BigDecimal paidAmount = amount.setScale(2);
        BigDecimal nextDue = pending.get(0).getTotalDue();
        BigDecimal remainingDue = pending.stream()
                .map(RepaymentSchedule::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<RepaymentSchedule> paidInstallments;
        if (paidAmount.compareTo(nextDue) == 0) {
            paidInstallments = List.of(pending.get(0));
        } else if (paidAmount.compareTo(remainingDue) == 0) {
            paidInstallments = pending;
        } else {
            throw new BusinessRuleException("INVALID_REPAYMENT_AMOUNT",
                    "Repayment amount must equal the next installment total " + nextDue
                            + " or the remaining balance " + remainingDue + ".");
        }

        LocalDateTime paidAt = LocalDateTime.now();
        BigDecimal principalPaid = BigDecimal.ZERO.setScale(2);
        for (RepaymentSchedule installment : paidInstallments) {
            installment.markPaid(paidAt);
            principalPaid = principalPaid.add(installment.getPrincipalDue());
        }
        loan.applyPrincipalPayment(principalPaid);
        if (loan.getOutstandingPrincipal().signum() == 0) {
            loan.close();
            requireApplication(loan.getApplicationId()).close();
        }
        schedules.saveSchedules(paidInstallments);
        loans.saveLoan(loan);
        return new RepaymentResult(loan, paidAmount, paidInstallments, schedules.findSchedulesByLoanId(loanId));
    }

    private Loan requireLoan(String loanId) {
        return loans.findByLoanId(loanId)
                .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Loan " + loanId + " not found"));
    }

    private LoanApplication requireApplication(String applicationId) {
        return applications.findByApplicationId(applicationId)
                .orElseThrow(() -> new NotFoundException("LOAN_APPLICATION_NOT_FOUND",
                        "Loan application " + applicationId + " not found"));
    }
}
