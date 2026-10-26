package com.carddemo.collections.interfaces.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 重算请求：{"daysPastDue":75}。
 */
public record RecalculateRequest(
        @NotNull(message = "Days past due must be supplied.")
        @Min(value = 1, message = "Days past due must be at least 1.")
        Integer daysPastDue) {
}
