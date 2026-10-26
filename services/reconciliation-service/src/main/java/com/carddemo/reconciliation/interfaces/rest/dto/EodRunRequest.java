package com.carddemo.reconciliation.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * EOD 请求：{"businessDate":"2026-10-26"}。
 */
public record EodRunRequest(
        @NotNull(message = "Business date must be supplied.")
        LocalDate businessDate) {
}
