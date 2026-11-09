package com.carddemo.marketing.domain.model;

import com.carddemo.marketing.domain.exception.InvalidRequestException;

import java.util.Locale;

/**
 * 反向交易类型（PRD REQ-CB-07）。
 */
public enum ReversalType {
    REFUND,
    CHARGEBACK;

    public static ReversalType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("INVALID_REVERSAL_TYPE", "Reversal type must be supplied.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_REVERSAL_TYPE",
                    "Reversal type must be one of REFUND, CHARGEBACK.");
        }
    }
}
