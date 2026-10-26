package com.carddemo.reference.interfaces.rest.dto;

import com.carddemo.reference.domain.model.ReferenceItemDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增参考数据请求。domain/code 会规范化为大写；sortOrder 默认 0，active 默认 true。
 */
public record ReferenceItemRequest(
        @Schema(example = "CURRENCY")
        @NotBlank(message = "Domain must be supplied.")
        @Size(max = 32, message = "Domain must be 32 characters or less.")
        String domain,

        @Schema(example = "JPY")
        @NotBlank(message = "Code must be supplied.")
        @Size(max = 32, message = "Code must be 32 characters or less.")
        String code,

        @Schema(example = "日元")
        @NotBlank(message = "Label must be supplied.")
        @Size(max = 120, message = "Label must be 120 characters or less.")
        String label,

        @Schema(example = "40", defaultValue = "0")
        @Min(value = 0, message = "sortOrder must not be negative.")
        Integer sortOrder,

        @Schema(example = "true", defaultValue = "true")
        Boolean active) {

    public ReferenceItemDraft toDraft() {
        return new ReferenceItemDraft(domain, code, label, sortOrder, active);
    }
}
