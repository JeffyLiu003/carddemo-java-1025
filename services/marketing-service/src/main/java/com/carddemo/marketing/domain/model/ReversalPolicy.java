package com.carddemo.marketing.domain.model;

import com.carddemo.marketing.domain.exception.InvalidRequestException;

import java.util.Locale;

/**
 * 追扣口径（设计 §4.1）。{@code null} 表示业务尚未确认，与 {@link #NONE}（明确不追扣）严格区分：
 * 前者受理时返回 409 并保留记录，后者只记录不追扣。
 */
public enum ReversalPolicy {
    FULL,
    PROPORTIONAL,
    NONE;

    public static ReversalPolicy parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_CAMPAIGN",
                    "Campaign reversalPolicy must be one of FULL, PROPORTIONAL, NONE.");
        }
    }
}
