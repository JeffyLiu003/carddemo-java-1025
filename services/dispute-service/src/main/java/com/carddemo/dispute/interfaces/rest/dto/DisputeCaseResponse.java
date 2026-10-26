package com.carddemo.dispute.interfaces.rest.dto;

import com.carddemo.dispute.domain.model.DisputeCase;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 争议案件响应：开案（201）、详情、分页与各状态迁移端点。
 */
public record DisputeCaseResponse(
        String caseId,
        String transactionId,
        String accountId,
        String customerId,
        String reasonCode,
        BigDecimal amount,
        String status,
        BigDecimal provisionalCreditAmount,
        LocalDateTime openedAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt) {

    public static DisputeCaseResponse from(DisputeCase disputeCase) {
        return new DisputeCaseResponse(
                disputeCase.getCaseId(),
                disputeCase.getTransactionId(),
                disputeCase.getAccountId(),
                disputeCase.getCustomerId(),
                disputeCase.getReasonCode(),
                disputeCase.getAmount(),
                disputeCase.getStatus().name(),
                disputeCase.getProvisionalCreditAmount(),
                disputeCase.getOpenedAt(),
                disputeCase.getUpdatedAt(),
                disputeCase.getClosedAt());
    }
}
