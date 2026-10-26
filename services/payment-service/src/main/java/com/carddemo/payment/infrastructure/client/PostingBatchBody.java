package com.carddemo.payment.infrastructure.client;

import java.math.BigDecimal;

/**
 * transaction-service 的 POST /api/v1/posting-batches 请求体（设计 §2.2）。
 */
record PostingBatchBody(
        String idempotencyKey,
        String typeCode,
        String accountId,
        BigDecimal amount,
        String currency,
        String description) {
}
