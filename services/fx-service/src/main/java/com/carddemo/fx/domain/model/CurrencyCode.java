package com.carddemo.fx.domain.model;

import com.carddemo.fx.domain.exception.BusinessRuleException;
import com.carddemo.fx.domain.exception.InvalidRequestException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ISO 4217 币种代码：3 位字母，统一大写存储。
 */
public final class CurrencyCode {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z]{3}$");

    private CurrencyCode() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("INVALID_CURRENCY", "Currency must be supplied.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new InvalidRequestException("INVALID_CURRENCY",
                    "Currency must be a 3-letter ISO code, got " + value + ".");
        }
        return normalized;
    }

    public static void requireDifferent(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            throw new BusinessRuleException("SAME_CURRENCY",
                    "From and to currency must differ, both are " + fromCurrency + ".");
        }
    }
}
