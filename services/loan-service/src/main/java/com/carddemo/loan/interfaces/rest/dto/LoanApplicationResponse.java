package com.carddemo.loan.interfaces.rest.dto;

import com.carddemo.loan.domain.model.LoanApplication;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 贷款申请响应：提交（201）、详情、分页、审批与拒绝。
 */
public record LoanApplicationResponse(
        String applicationId,
        String customerId,
        String productCode,
        BigDecimal principal,
        int termMonths,
        BigDecimal annualRate,
        String status,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime decidedAt) {

    public static LoanApplicationResponse from(LoanApplication application) {
        return new LoanApplicationResponse(
                application.getApplicationId(),
                application.getCustomerId(),
                application.getProductCode(),
                application.getPrincipal(),
                application.getTermMonths(),
                application.getAnnualRate(),
                application.getStatus().name(),
                application.getRejectReason(),
                application.getCreatedAt(),
                application.getDecidedAt());
    }
}
