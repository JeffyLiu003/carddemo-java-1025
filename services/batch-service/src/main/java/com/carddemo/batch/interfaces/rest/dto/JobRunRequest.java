package com.carddemo.batch.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 执行作业请求：{"jobCode":"INTEREST_RUN","businessDate":"2026-10-26"}。
 */
public record JobRunRequest(
        @NotBlank(message = "Job code must be supplied.")
        String jobCode,

        @NotNull(message = "Business date must be supplied.")
        LocalDate businessDate) {
}
