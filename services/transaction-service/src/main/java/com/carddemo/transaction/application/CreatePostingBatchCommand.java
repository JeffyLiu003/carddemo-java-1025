package com.carddemo.transaction.application;

import java.math.BigDecimal;

/**
 * 创建过账批次入参（契约 §2.3 创建请求）。
 */
public record CreatePostingBatchCommand(
        String idempotencyKey,
        String typeCode,
        String accountId,
        BigDecimal amount,
        String currency,
        String description) {
}
