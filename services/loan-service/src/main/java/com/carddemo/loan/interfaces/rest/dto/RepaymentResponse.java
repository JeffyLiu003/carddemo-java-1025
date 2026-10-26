package com.carddemo.loan.interfaces.rest.dto;

import com.carddemo.loan.application.RepaymentResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * 还款响应：冲抵的期次号、贷款最新状态与完整计划。
 */
public record RepaymentResponse(String loanId, BigDecimal amount, List<Integer> paidInstallments,
                                BigDecimal outstandingPrincipal, String loanStatus,
                                List<RepaymentScheduleResponse> schedule) {

    public static RepaymentResponse from(RepaymentResult result) {
        return new RepaymentResponse(
                result.loan().getLoanId(),
                result.amount(),
                result.paidInstallments().stream().map(installment -> installment.getInstallmentNo()).toList(),
                result.loan().getOutstandingPrincipal(),
                result.loan().getStatus().name(),
                result.schedule().stream().map(RepaymentScheduleResponse::from).toList());
    }
}
