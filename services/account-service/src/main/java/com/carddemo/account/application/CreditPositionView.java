package com.carddemo.account.application;

import java.math.BigDecimal;

/**
 * 账户额度视图：供接口层组装响应，避免应用层依赖 REST DTO。
 */
public record CreditPositionView(
        String accountId,
        String productCode,
        BigDecimal creditLimit,
        BigDecimal currentBalance,
        BigDecimal activeHoldAmount,
        BigDecimal availableCredit) {
}
