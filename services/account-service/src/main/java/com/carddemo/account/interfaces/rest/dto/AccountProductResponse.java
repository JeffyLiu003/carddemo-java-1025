package com.carddemo.account.interfaces.rest.dto;

import com.carddemo.account.domain.model.AccountProduct;

import java.math.BigDecimal;

public record AccountProductResponse(
        String productCode,
        String name,
        String category,
        String currency,
        BigDecimal defaultCreditLimit,
        BigDecimal maxCreditLimit,
        BigDecimal defaultCashLimit,
        BigDecimal maxCashLimit,
        BigDecimal apr,
        BigDecimal monthlyFee,
        int statementCycleDay,
        boolean active) {

    public static AccountProductResponse from(AccountProduct product) {
        return new AccountProductResponse(
                product.getProductCode(),
                product.getName(),
                product.getCategory().name(),
                product.getCurrency(),
                product.getDefaultCreditLimit(),
                product.getMaxCreditLimit(),
                product.getDefaultCashLimit(),
                product.getMaxCashLimit(),
                product.getApr(),
                product.getMonthlyFee(),
                product.getStatementCycleDay(),
                product.isActive());
    }
}
