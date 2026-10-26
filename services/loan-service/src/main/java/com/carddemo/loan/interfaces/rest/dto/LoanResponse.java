package com.carddemo.loan.interfaces.rest.dto;

import com.carddemo.loan.domain.model.Loan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 贷款响应：放款、详情与还款结果中的贷款快照。
 */
public record LoanResponse(
        String loanId,
        String applicationId,
        String customerId,
        BigDecimal principal,
        BigDecimal outstandingPrincipal,
        int termMonths,
        BigDecimal annualRate,
        String status,
        LocalDateTime disbursedAt) {

    public static LoanResponse from(Loan loan) {
        return new LoanResponse(
                loan.getLoanId(),
                loan.getApplicationId(),
                loan.getCustomerId(),
                loan.getPrincipal(),
                loan.getOutstandingPrincipal(),
                loan.getTermMonths(),
                loan.getAnnualRate(),
                loan.getStatus().name(),
                loan.getDisbursedAt());
    }
}
