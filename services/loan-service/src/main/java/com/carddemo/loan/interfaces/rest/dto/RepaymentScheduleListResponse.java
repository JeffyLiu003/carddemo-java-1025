package com.carddemo.loan.interfaces.rest.dto;

import java.util.List;

/**
 * GET /api/v1/loans/{loanId}/schedule 响应。
 */
public record RepaymentScheduleListResponse(String loanId, List<RepaymentScheduleResponse> items) {
}
