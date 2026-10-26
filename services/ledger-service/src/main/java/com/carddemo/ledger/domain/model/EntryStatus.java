package com.carddemo.ledger.domain.model;

/**
 * 日记账分录状态。冲正不删除原分录，只把原分录标记为 REVERSED。
 */
public enum EntryStatus {
    POSTED,
    REVERSED
}
