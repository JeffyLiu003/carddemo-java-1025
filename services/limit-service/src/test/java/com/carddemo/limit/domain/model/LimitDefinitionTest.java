package com.carddemo.limit.domain.model;

import com.carddemo.limit.domain.exception.BusinessRuleException;
import com.carddemo.limit.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitDefinitionTest {

    private static LimitDefinition limit(String amount) {
        return LimitDefinition.create("limit-1", ScopeType.ACCOUNT, "00000000001", LimitType.PURCHASE,
                "usd", new BigDecimal(amount), true);
    }

    @Test
    void createsNormalizedLimitWithZeroUsage() {
        LimitDefinition definition = limit("1000.00");

        assertThat(definition.getScopeType()).isEqualTo(ScopeType.ACCOUNT);
        assertThat(definition.getScopeId()).isEqualTo("00000000001");
        assertThat(definition.getLimitType()).isEqualTo(LimitType.PURCHASE);
        assertThat(definition.getCurrency()).isEqualTo("USD");
        assertThat(definition.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(definition.getUsedAmount()).isEqualByComparingTo("0.00");
        assertThat(definition.remainingAmount()).isEqualByComparingTo("1000.00");
        assertThat(definition.isActive()).isTrue();
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> limit("0.00"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_LIMIT_AMOUNT");
        assertThatThrownBy(() -> limit("-10.00"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_LIMIT_AMOUNT");
    }

    @Test
    void rejectsBlankScopeAndMalformedCurrency() {
        assertThatThrownBy(() -> LimitDefinition.create("limit-1", ScopeType.CUSTOMER, " ",
                LimitType.CASH, "USD", new BigDecimal("100.00"), true))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_LIMIT_SCOPE");
        assertThatThrownBy(() -> LimitDefinition.create("limit-1", ScopeType.CUSTOMER, "000000001",
                LimitType.CASH, "US", new BigDecimal("100.00"), true))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_CURRENCY");
    }

    @Test
    void reserveAccumulatesUsageAndRejectsExcess() {
        LimitDefinition definition = limit("100.00");

        definition.reserve(new BigDecimal("60.00"));
        definition.reserve(new BigDecimal("40.00"));

        assertThat(definition.getUsedAmount()).isEqualByComparingTo("100.00");
        assertThat(definition.remainingAmount()).isEqualByComparingTo("0.00");

        assertThatThrownBy(() -> definition.reserve(new BigDecimal("0.01")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LIMIT_EXCEEDED");
        assertThat(definition.getUsedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void releaseRestoresUsageAndRejectsNegativeResult() {
        LimitDefinition definition = limit("100.00");
        definition.reserve(new BigDecimal("50.00"));

        definition.release(new BigDecimal("20.00"));
        assertThat(definition.getUsedAmount()).isEqualByComparingTo("30.00");

        assertThatThrownBy(() -> definition.release(new BigDecimal("30.01")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_LIMIT_RELEASE");
        assertThat(definition.getUsedAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void adjustRejectsAmountBelowUsed() {
        LimitDefinition definition = limit("100.00");
        definition.reserve(new BigDecimal("30.00"));

        assertThatThrownBy(() -> definition.adjust(new BigDecimal("29.99")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LIMIT_BELOW_USED");

        definition.adjust(new BigDecimal("500.00"));
        assertThat(definition.getAmount()).isEqualByComparingTo("500.00");
        assertThat(definition.remainingAmount()).isEqualByComparingTo("470.00");
    }

    @Test
    void allowsOnlyRequestsWithinRemainingAndWhenActive() {
        LimitDefinition definition = limit("100.00");
        definition.reserve(new BigDecimal("70.00"));

        assertThat(definition.allows(new BigDecimal("30.00"))).isTrue();
        assertThat(definition.allows(new BigDecimal("30.01"))).isFalse();

        LimitDefinition inactive = LimitDefinition.create("limit-2", ScopeType.CHANNEL, "WEB",
                LimitType.TRANSFER, "USD", new BigDecimal("100.00"), false);

        assertThat(inactive.allows(new BigDecimal("1.00"))).isFalse();
        assertThatThrownBy(() -> inactive.reserve(new BigDecimal("1.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LIMIT_INACTIVE");
    }
}
