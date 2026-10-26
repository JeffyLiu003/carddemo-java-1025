package com.carddemo.fraud.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;

/**
 * 评分规则。命中条件由规则类型决定：金额大于阈值、商户名不区分大小写包含、国家在名单内。
 */
@Entity
@Table(name = "fraud_rule")
public class FraudRule {

    @Id
    @Column(name = "rule_code", length = 32, nullable = false, updatable = false)
    private String ruleCode;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", length = 24, nullable = false)
    private FraudRuleType ruleType;

    @Column(name = "threshold_amount", precision = 12, scale = 2)
    private BigDecimal thresholdAmount;

    @Column(name = "match_value", length = 100)
    private String matchValue;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FraudRule() {
        // for JPA
    }

    public FraudRule(String ruleCode, String name, FraudRuleType ruleType, BigDecimal thresholdAmount,
                     String matchValue, int score, boolean active) {
        this.ruleCode = ruleCode;
        this.name = name;
        this.ruleType = ruleType;
        this.thresholdAmount = thresholdAmount;
        this.matchValue = matchValue;
        this.score = score;
        this.active = active;
    }

    public boolean matches(FraudEvaluationInput input) {
        if (!active || ruleType == null || input == null) {
            return false;
        }
        return switch (ruleType) {
            case AMOUNT_GT -> thresholdAmount != null && input.amount() != null
                    && input.amount().compareTo(thresholdAmount) > 0;
            case MERCHANT_BLOCKED -> matchesMerchant(input.merchantName());
            case COUNTRY_BLOCKED -> matchesCountry(input.country());
        };
    }

    private boolean matchesMerchant(String merchantName) {
        if (matchValue == null || matchValue.isBlank() || merchantName == null) {
            return false;
        }
        return merchantName.toLowerCase(Locale.ROOT)
                .contains(matchValue.trim().toLowerCase(Locale.ROOT));
    }

    private boolean matchesCountry(String country) {
        if (matchValue == null || matchValue.isBlank() || country == null) {
            return false;
        }
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(matchValue.split(","))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .anyMatch(normalized::equals);
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getName() {
        return name;
    }

    public FraudRuleType getRuleType() {
        return ruleType;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public String getMatchValue() {
        return matchValue;
    }

    public int getScore() {
        return score;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
