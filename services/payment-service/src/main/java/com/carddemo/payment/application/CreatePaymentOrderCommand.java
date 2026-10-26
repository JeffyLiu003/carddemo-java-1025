package com.carddemo.payment.application;

import java.math.BigDecimal;

/**
 * 创建支付指令入参（设计 §2.2 创建请求）。
 */
public record CreatePaymentOrderCommand(
        String idempotencyKey,
        String debtorAccountId,
        String creditorName,
        String creditorAccountNumber,
        String creditorBankCode,
        BigDecimal amount,
        String currency,
        String paymentType,
        String reference,
        String description) {
}
