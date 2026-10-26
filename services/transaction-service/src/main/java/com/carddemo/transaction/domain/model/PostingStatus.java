package com.carddemo.transaction.domain.model;

/**
 * 过账批次状态：POSTED 有效，REVERSED 已被冲正（冲正不删除原批次）。
 */
public enum PostingStatus {

    POSTED,
    REVERSED
}
