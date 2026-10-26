package com.carddemo.dispute.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/disputes/{id}/resolve 请求体：won=true 判持卡人胜（WON），false 判商户胜（LOST）。
 */
public record ResolveDisputeRequest(
        @NotNull(message = "must be supplied")
        Boolean won) {
}
