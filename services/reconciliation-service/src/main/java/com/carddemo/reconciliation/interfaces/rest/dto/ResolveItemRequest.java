package com.carddemo.reconciliation.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 差异处理请求：{"note":"已确认总账同步延迟，手工补录"}。
 */
public record ResolveItemRequest(
        @NotBlank(message = "Resolution note must be supplied.")
        String note) {
}
