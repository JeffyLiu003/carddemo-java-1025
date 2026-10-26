package com.carddemo.aml.application;

import com.carddemo.aml.domain.model.AmlAlert;
import com.carddemo.aml.domain.model.ScreeningResult;

/**
 * 筛查结果：警报仅在 POTENTIAL_MATCH/BLOCKED 时生成，故可能为 null。
 */
public record ScreeningOutcome(ScreeningResult screening, AmlAlert alert) {
}
