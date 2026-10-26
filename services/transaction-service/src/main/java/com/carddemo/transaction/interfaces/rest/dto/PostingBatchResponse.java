package com.carddemo.transaction.interfaces.rest.dto;

import com.carddemo.transaction.application.PostingBatchView;
import com.carddemo.transaction.domain.model.PostingBatch;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 过账批次响应：POST /api/v1/posting-batches（201）、GET /{batchId}、POST /{batchId}/reversal。
 */
public record PostingBatchResponse(
        String batchId,
        String idempotencyKey,
        String typeCode,
        String typeName,
        String accountId,
        BigDecimal amount,
        String currency,
        String description,
        String status,
        String reversalOf,
        String ledgerEntryId,
        LocalDateTime createdAt,
        LocalDateTime reversedAt,
        List<PostingLegResponse> legs) {

    public static PostingBatchResponse from(PostingBatchView view) {
        PostingBatch batch = view.batch();
        return new PostingBatchResponse(
                batch.getBatchId(),
                batch.getIdempotencyKey(),
                batch.getTypeCode(),
                view.typeName(),
                batch.getAccountId(),
                batch.getAmount(),
                batch.getCurrency(),
                batch.getDescription(),
                batch.getStatus().name(),
                batch.getReversalOf(),
                batch.getLedgerEntryId(),
                batch.getCreatedAt(),
                batch.getReversedAt(),
                batch.getLegs().stream().map(PostingLegResponse::from).toList());
    }
}
