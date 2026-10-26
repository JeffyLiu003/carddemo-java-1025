package com.carddemo.loan.application;

import com.carddemo.loan.domain.model.Loan;
import com.carddemo.loan.domain.model.LoanApplication;
import com.carddemo.loan.domain.model.RepaymentSchedule;

import java.util.List;

/**
 * 审批结果：申请、新贷款账户与生成的还款计划。
 */
public record LoanApproval(LoanApplication application, Loan loan, List<RepaymentSchedule> schedule) {
}
