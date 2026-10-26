package com.carddemo.billing.domain.model;

import java.math.BigDecimal;

/**
 * 产品快照（account-service GET /api/v1/account-products/{productCode}），账单只关心 APR。
 */
public record AccountProductSnapshot(String productCode, BigDecimal apr) {
}
