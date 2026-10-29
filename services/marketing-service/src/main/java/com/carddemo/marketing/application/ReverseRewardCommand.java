package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.ReversalType;

import java.math.BigDecimal;

/**
 * 退款与冲正受理输入（设计 §5.1）。
 */
public record ReverseRewardCommand(
        String reversalTransactionId,
        String originalTransactionId,
        ReversalType reversalType,
        BigDecimal reversalAmount) {
}
