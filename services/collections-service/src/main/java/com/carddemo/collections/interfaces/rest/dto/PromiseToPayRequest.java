package com.carddemo.collections.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 承诺还款请求：{"date":"2026-11-05"}，日期必须晚于当天。
 */
public record PromiseToPayRequest(
        @NotNull(message = "Promise date must be supplied.")
        LocalDate date) {
}
