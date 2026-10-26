package com.carddemo.search.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * transaction-service 列表投影。
 */
public record TransactionSummary(
        String transactionId,
        String accountId,
        String cardNumber,
        BigDecimal amount,
        String description,
        LocalDateTime processedTimestamp) {
}
