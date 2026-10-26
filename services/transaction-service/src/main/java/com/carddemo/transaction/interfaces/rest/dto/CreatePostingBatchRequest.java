package com.carddemo.transaction.interfaces.rest.dto;

import com.carddemo.transaction.application.CreatePostingBatchCommand;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * POST /api/v1/posting-batches 请求（docs/design/11-money-path-slice-a2.md §2.3）。
 */
public record CreatePostingBatchRequest(
        @NotBlank String idempotencyKey,
        @NotBlank String typeCode,
        @NotBlank String accountId,
        BigDecimal amount,
        String currency,
        String description) {

    public CreatePostingBatchCommand toCommand() {
        return new CreatePostingBatchCommand(idempotencyKey, typeCode, accountId, amount, currency, description);
    }
}
