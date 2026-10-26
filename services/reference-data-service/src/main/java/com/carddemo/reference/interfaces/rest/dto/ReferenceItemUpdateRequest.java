package com.carddemo.reference.interfaces.rest.dto;

import com.carddemo.reference.domain.model.ReferenceItemUpdate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改参考数据请求：只允许改 label/sortOrder/active；sortOrder 默认 0，active 默认 true。
 */
public record ReferenceItemUpdateRequest(
        @Schema(example = "日元（JPY）")
        @NotBlank(message = "Label must be supplied.")
        @Size(max = 120, message = "Label must be 120 characters or less.")
        String label,

        @Schema(example = "40", defaultValue = "0")
        @Min(value = 0, message = "sortOrder must not be negative.")
        Integer sortOrder,

        @Schema(example = "true", defaultValue = "true")
        Boolean active) {

    public ReferenceItemUpdate toUpdate() {
        return new ReferenceItemUpdate(label, sortOrder, active);
    }
}
