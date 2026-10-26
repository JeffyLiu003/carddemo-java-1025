package com.carddemo.loan.application;

import com.carddemo.loan.domain.model.Loan;
import com.carddemo.loan.domain.model.RepaymentSchedule;

import java.math.BigDecimal;
import java.util.List;

/**
 * 还款结果：本次冲抵的期次、冲抵后的贷款与完整计划。
 */
public record RepaymentResult(Loan loan, BigDecimal amount, List<RepaymentSchedule> paidInstallments,
                              List<RepaymentSchedule> schedule) {
}
