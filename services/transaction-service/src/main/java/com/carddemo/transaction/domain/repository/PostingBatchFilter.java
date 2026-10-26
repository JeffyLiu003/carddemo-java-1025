package com.carddemo.transaction.domain.repository;

/**
 * 过账批次查询条件：账户号与幂等键均可选。
 */
public record PostingBatchFilter(String accountId, String idempotencyKey) {
}
