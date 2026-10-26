package com.carddemo.limit.application;

import com.carddemo.limit.domain.model.LimitType;
import com.carddemo.limit.domain.model.ScopeType;

import java.math.BigDecimal;

/**
 * 创建限额命令。
 */
public record CreateLimitCommand(
        ScopeType scopeType,
        String scopeId,
        LimitType limitType,
        String currency,
        BigDecimal amount,
        boolean active) {
}
