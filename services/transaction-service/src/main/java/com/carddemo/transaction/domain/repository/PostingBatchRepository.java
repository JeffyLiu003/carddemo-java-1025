package com.carddemo.transaction.domain.repository;

import com.carddemo.transaction.domain.model.PostingBatch;

import java.util.Optional;

/**
 * 过账批次仓储端口。实现见 infrastructure.persistence.PostingBatchJpaRepository。
 */
public interface PostingBatchRepository {

    PostingBatch saveBatch(PostingBatch batch);

    Optional<PostingBatch> findByBatchId(String batchId);

    Optional<PostingBatch> findByIdempotencyKey(String idempotencyKey);

    /**
     * 原批次对应的冲正批次（冲正幂等）。
     */
    Optional<PostingBatch> findByReversalOf(String reversalOf);

    PageResult<PostingBatch> search(PostingBatchFilter filter, int page, int size);
}
