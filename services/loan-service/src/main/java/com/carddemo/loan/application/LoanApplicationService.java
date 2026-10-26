package com.carddemo.loan.application;

import com.carddemo.loan.domain.exception.BusinessRuleException;
import com.carddemo.loan.domain.exception.InvalidRequestException;
import com.carddemo.loan.domain.exception.NotFoundException;
import com.carddemo.loan.domain.model.Loan;
import com.carddemo.loan.domain.model.LoanApplication;
import com.carddemo.loan.domain.model.LoanApplicationStatus;
import com.carddemo.loan.domain.model.RepaymentSchedule;
import com.carddemo.loan.domain.repository.LoanApplicationRepository;
import com.carddemo.loan.domain.repository.LoanRepository;
import com.carddemo.loan.domain.repository.PageResult;
import com.carddemo.loan.domain.repository.RepaymentScheduleRepository;
import com.carddemo.loan.domain.service.AmortizationCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 贷款申请用例（设计 §2.2）：提交、查询、审批（生成贷款与等额本息计划）、拒绝。
 */
@Service
@Transactional
public class LoanApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final LoanApplicationRepository applications;
    private final LoanRepository loans;
    private final RepaymentScheduleRepository schedules;

    public LoanApplicationService(LoanApplicationRepository applications, LoanRepository loans,
                                  RepaymentScheduleRepository schedules) {
        this.applications = applications;
        this.loans = loans;
        this.schedules = schedules;
    }

    /**
     * 提交申请：初始状态 SUBMITTED；金额、期限、利率按设计 §2.2 校验。
     */
    public LoanApplication submit(CreateLoanApplicationCommand command) {
        if (command.termMonths() == null) {
            throw new InvalidRequestException("TERM_MONTHS_REQUIRED", "Loan term must be supplied.");
        }
        LoanApplication application = LoanApplication.submit(UUID.randomUUID().toString(),
                command.customerId(), command.productCode(), command.principal(),
                command.termMonths(), command.annualRate(), LocalDateTime.now());
        return applications.saveApplication(application);
    }

    @Transactional(readOnly = true)
    public LoanApplication get(String applicationId) {
        return requireApplication(applicationId);
    }

    @Transactional(readOnly = true)
    public PageResult<LoanApplication> list(String status, int page, int size) {
        return applications.search(parseStatusFilter(status), Math.max(page, 0),
                size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    /**
     * 审批通过：SUBMITTED → APPROVED，同时建账贷款并生成还款计划。
     * 首期到期日为审批日 + 1 个月（尚未放款时以审批日为基准），此后每月同日。
     */
    public LoanApproval approve(String applicationId) {
        LoanApplication application = requireApplication(applicationId);
        LocalDateTime decidedAt = LocalDateTime.now();
        application.approve(decidedAt);

        Loan loan = Loan.originate(UUID.randomUUID().toString(), application);
        LocalDate firstDueDate = decidedAt.toLocalDate().plusMonths(1);
        List<RepaymentSchedule> schedule = AmortizationCalculator
                .buildSchedule(application.getPrincipal(), application.getAnnualRate(),
                        application.getTermMonths(), firstDueDate)
                .stream()
                .map(line -> RepaymentSchedule.installment(UUID.randomUUID().toString(), loan.getLoanId(),
                        line.installmentNo(), line.dueDate(), line.principalDue(), line.interestDue()))
                .toList();

        loans.saveLoan(loan);
        schedules.saveSchedules(schedule);
        applications.saveApplication(application);
        return new LoanApproval(application, loan, schedule);
    }

    /**
     * 拒绝：SUBMITTED → REJECTED。
     */
    public LoanApplication reject(String applicationId, String reason) {
        LoanApplication application = requireApplication(applicationId);
        application.reject(reason, LocalDateTime.now());
        return applications.saveApplication(application);
    }

    private LoanApplication requireApplication(String applicationId) {
        return applications.findByApplicationId(applicationId)
                .orElseThrow(() -> new NotFoundException("LOAN_APPLICATION_NOT_FOUND",
                        "Loan application " + applicationId + " not found"));
    }

    private static LoanApplicationStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        for (LoanApplicationStatus candidate : LoanApplicationStatus.values()) {
            if (candidate.name().equalsIgnoreCase(status.trim())) {
                return candidate;
            }
        }
        throw new InvalidRequestException("INVALID_APPLICATION_STATUS",
                "Status filter must be one of SUBMITTED, APPROVED, REJECTED, DISBURSED, CLOSED.");
    }
}
