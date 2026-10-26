package com.carddemo.payment.interfaces.rest.dto;

import com.carddemo.payment.application.CreatePaymentOrderCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * POST /api/v1/payment-orders 请求（docs/design/13-payments-billing-limits-fx.md §2.2）。
 */
public record CreatePaymentOrderRequest(
        @NotBlank String idempotencyKey,
        @NotBlank String debtorAccountId,
        String creditorName,
        String creditorAccountNumber,
        String creditorBankCode,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String paymentType,
        String reference,
        String description) {

    public CreatePaymentOrderCommand toCommand() {
        return new CreatePaymentOrderCommand(idempotencyKey, debtorAccountId, creditorName,
                creditorAccountNumber, creditorBankCode, amount, currency, paymentType, reference,
                description);
    }
}
