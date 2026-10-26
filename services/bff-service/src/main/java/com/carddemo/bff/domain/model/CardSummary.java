package com.carddemo.bff.domain.model;

import java.time.LocalDate;

/**
 * 账户下的卡片摘要（不包含 CVV 等敏感字段）。
 */
public record CardSummary(
        String cardNumber,
        String customerId,
        String embossedName,
        LocalDate expiryDate,
        String activeStatus) {
}
