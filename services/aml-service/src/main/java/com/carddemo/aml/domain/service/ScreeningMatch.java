package com.carddemo.aml.domain.service;

import com.carddemo.aml.domain.model.SanctionsEntry;
import com.carddemo.aml.domain.model.ScreeningResultStatus;

/**
 * 筛查命中：结论、命中条目（CLEAR 时为 null）与分值。
 */
public record ScreeningMatch(ScreeningResultStatus result, SanctionsEntry matchedEntry, int score) {
}
