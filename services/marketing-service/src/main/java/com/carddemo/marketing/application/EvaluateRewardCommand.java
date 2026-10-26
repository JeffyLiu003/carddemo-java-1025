package com.carddemo.marketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易返现评估输入（设计 §4 评估请求示例）。
 */
public record EvaluateRewardCommand(
        String transactionId,
        String accountId,
        String cardNumber,
        String customerId,
        BigDecimal amount,
        LocalDateTime occurredAt) {
}
