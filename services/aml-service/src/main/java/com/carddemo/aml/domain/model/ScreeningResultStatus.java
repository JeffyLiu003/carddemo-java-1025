package com.carddemo.aml.domain.model;

/**
 * 筛查结论。完全匹配 BLOCKED、包含匹配 POTENTIAL_MATCH、否则 CLEAR。
 */
public enum ScreeningResultStatus {

    CLEAR,
    POTENTIAL_MATCH,
    BLOCKED
}
