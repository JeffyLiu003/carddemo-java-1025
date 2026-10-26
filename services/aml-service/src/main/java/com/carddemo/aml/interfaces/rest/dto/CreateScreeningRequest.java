package com.carddemo.aml.interfaces.rest.dto;

import com.carddemo.aml.application.ScreeningCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 筛查请求（POST /api/v1/screenings）。country/entityType 仅作记录，不参与匹配。
 */
public record CreateScreeningRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 3) String country,
        @Size(max = 24) String entityType,
        @Size(max = 9) String customerId) {

    public ScreeningCommand toCommand() {
        return new ScreeningCommand(name, country, entityType, customerId);
    }
}
