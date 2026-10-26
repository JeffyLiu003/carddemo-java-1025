package com.carddemo.ledger.domain.repository;

import com.carddemo.ledger.domain.model.Direction;

import java.math.BigDecimal;

/**
 * 试算平衡的原始聚合行：某科目某方向的合计发生额。
 */
public record AccountActivity(String accountCode, Direction direction, BigDecimal amount) {
}
