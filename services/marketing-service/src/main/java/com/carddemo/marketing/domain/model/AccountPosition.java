package com.carddemo.marketing.domain.model;

import java.math.BigDecimal;

/**
 * 账户在营销视角的快照：产品等级决定资格圈选，activeStatus 决定可否报名/返现。
 *
 * <p>{@code activeStatus} 缺省为 true（account-service 的 credit-position 未暴露该字段时按可用处理）。
 */
public record AccountPosition(
        String accountId,
        String productCode,
        BigDecimal currentBalance,
        BigDecimal availableCredit,
        boolean activeStatus) {
}
