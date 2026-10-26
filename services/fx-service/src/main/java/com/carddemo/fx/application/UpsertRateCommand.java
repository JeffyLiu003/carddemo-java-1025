package com.carddemo.fx.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率维护（upsert）命令。
 */
public record UpsertRateCommand(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        LocalDateTime effectiveAt) {
}
