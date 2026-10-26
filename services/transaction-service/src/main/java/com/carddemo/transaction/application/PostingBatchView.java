package com.carddemo.transaction.application;

import com.carddemo.transaction.domain.model.PostingBatch;

/**
 * 过账批次视图：批次聚合 + 类型名称（响应需要 typeName）。
 */
public record PostingBatchView(PostingBatch batch, String typeName) {
}
