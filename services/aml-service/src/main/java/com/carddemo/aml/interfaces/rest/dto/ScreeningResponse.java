package com.carddemo.aml.interfaces.rest.dto;

import com.carddemo.aml.application.ScreeningOutcome;
import com.carddemo.aml.domain.model.ScreeningResult;
import com.carddemo.aml.domain.model.ScreeningResultStatus;

import java.time.LocalDateTime;

/**
 * 筛查响应：命中条目与结论；POTENTIAL_MATCH/BLOCKED 时附带生成的警报 id。
 */
public record ScreeningResponse(
        String screeningId,
        String name,
        String country,
        String entityType,
        ScreeningResultStatus result,
        String matchedEntryId,
        int score,
        String alertId,
        LocalDateTime createdAt) {

    public static ScreeningResponse from(ScreeningOutcome outcome) {
        ScreeningResult screening = outcome.screening();
        return new ScreeningResponse(
                screening.getScreeningId(),
                screening.getName(),
                screening.getCountry(),
                screening.getEntityType(),
                screening.getResult(),
                screening.getMatchedEntryId(),
                screening.getScore(),
                outcome.alert() == null ? null : outcome.alert().getAlertId(),
                screening.getCreatedAt());
    }
}
