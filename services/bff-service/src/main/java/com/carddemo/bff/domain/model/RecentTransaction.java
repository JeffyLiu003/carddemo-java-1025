package com.carddemo.bff.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 最近交易（概览只取最新若干笔）。
 */
public record RecentTransaction(
        String transactionId,
        BigDecimal amount,
        String description,
        LocalDateTime processedTimestamp) {
}
