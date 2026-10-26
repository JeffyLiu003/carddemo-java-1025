package com.carddemo.fraud.application;

import java.math.BigDecimal;

/**
 * 评分命令：交易标识、账户、金额、商户与收单国家。
 */
public record EvaluationCommand(
        String transactionId,
        String accountId,
        BigDecimal amount,
        String merchantName,
        String country) {
}
