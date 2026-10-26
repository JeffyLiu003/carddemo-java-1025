package com.carddemo.limit.application;

import com.carddemo.limit.domain.model.LimitType;
import com.carddemo.limit.domain.model.ScopeType;

import java.math.BigDecimal;

/**
 * 可用性检查结果。不存在匹配限额时 allowed=false、remainingAmount=0。
 */
public record LimitCheckView(
        boolean allowed,
        String limitId,
        ScopeType scopeType,
        String scopeId,
        LimitType limitType,
        String currency,
        BigDecimal amount,
        BigDecimal usedAmount,
        BigDecimal remainingAmount) {
}
