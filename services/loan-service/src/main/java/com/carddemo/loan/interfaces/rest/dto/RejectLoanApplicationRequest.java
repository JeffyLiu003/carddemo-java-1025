package com.carddemo.loan.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/loan-applications/{id}/reject 请求体。
 */
public record RejectLoanApplicationRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String reason) {
}
