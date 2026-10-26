package com.carddemo.payment.interfaces.rest.dto;

import com.carddemo.payment.domain.model.PaymentOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付指令响应：POST /api/v1/payment-orders（201）、GET /{paymentId} 与各状态迁移端点。
 */
public record PaymentOrderResponse(
        String paymentId,
        String idempotencyKey,
        String debtorAccountId,
        String creditorName,
        String creditorAccountNumber,
        String creditorBankCode,
        BigDecimal amount,
        String currency,
        String paymentType,
        String status,
        String reference,
        String description,
        String transactionBatchId,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime settledAt,
        LocalDateTime returnedAt) {

    public static PaymentOrderResponse from(PaymentOrder order) {
        return new PaymentOrderResponse(
                order.getPaymentId(),
                order.getIdempotencyKey(),
                order.getDebtorAccountId(),
                order.getCreditorName(),
                order.getCreditorAccountNumber(),
                order.getCreditorBankCode(),
                order.getAmount(),
                order.getCurrency(),
                order.getPaymentType().name(),
                order.getStatus().name(),
                order.getReference(),
                order.getDescription(),
                order.getTransactionBatchId(),
                order.getRejectReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getSettledAt(),
                order.getReturnedAt());
    }
}
