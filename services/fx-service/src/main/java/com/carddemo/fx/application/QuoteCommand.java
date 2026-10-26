package com.carddemo.fx.application;

import java.math.BigDecimal;

/**
 * 报价命令。
 */
public record QuoteCommand(
        String fromCurrency,
        String toCurrency,
        BigDecimal amount) {
}
