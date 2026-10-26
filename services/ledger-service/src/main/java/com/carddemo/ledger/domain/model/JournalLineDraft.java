package com.carddemo.ledger.domain.model;

import java.math.BigDecimal;

/**
 * 待记账的一行（请求侧投影）。行号由 {@link JournalEntry} 按顺序分配。
 */
public record JournalLineDraft(String accountCode, Direction direction, BigDecimal amount) {
}
