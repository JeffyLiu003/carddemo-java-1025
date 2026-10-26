package com.carddemo.reporting.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 生成报表请求：{"reportCode":"DAILY_TRANSACTION_SUMMARY","businessDate":"2026-10-26"}。
 */
public record GenerateReportRequest(
        @NotBlank(message = "Report code must be supplied.")
        String reportCode,
        @NotNull(message = "Business date must be supplied.")
        LocalDate businessDate) {
}
