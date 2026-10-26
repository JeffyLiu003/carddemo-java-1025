package com.carddemo.audit.interfaces.rest.dto;

import com.carddemo.audit.domain.model.AuditEventDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 追加审计事件请求。actor/action/resourceType/resourceId 必填，payload 为原始 JSON 文本（可空）。
 */
public record AuditEventRequest(
        @Schema(example = "demo.operator")
        @NotBlank(message = "Actor must be supplied.")
        @Size(max = 64, message = "Actor must be 64 characters or less.")
        String actor,

        @Schema(example = "CARD_STATUS_CHANGED")
        @NotBlank(message = "Action must be supplied.")
        @Size(max = 80, message = "Action must be 80 characters or less.")
        String action,

        @Schema(example = "CARD")
        @NotBlank(message = "Resource type must be supplied.")
        @Size(max = 40, message = "Resource type must be 40 characters or less.")
        String resourceType,

        @Schema(example = "3344556677889900")
        @NotBlank(message = "Resource id must be supplied.")
        @Size(max = 64, message = "Resource id must be 64 characters or less.")
        String resourceId,

        @Schema(example = "{\"from\":\"ACTIVE\",\"to\":\"BLOCKED\"}")
        String payload) {

    public AuditEventDraft toDraft() {
        return new AuditEventDraft(actor, action, resourceType, resourceId, payload);
    }
}
