package com.carddemo.fraud.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 评分结果（不可变记录）。matched_rules 以逗号分隔保存命中规则编码。
 */
@Entity
@Table(name = "fraud_evaluation")
public class FraudEvaluation {

    @Id
    @Column(name = "evaluation_id", length = 36, nullable = false, updatable = false)
    private String evaluationId;

    @Column(name = "transaction_id", length = 36, nullable = false)
    private String transactionId;

    @Column(name = "account_id", length = 11, nullable = false)
    private String accountId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "merchant_name", length = 50)
    private String merchantName;

    @Column(name = "country", length = 3)
    private String country;

    @Column(name = "score", nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 10, nullable = false)
    private FraudDecision decision;

    @Column(name = "matched_rules", length = 200)
    private String matchedRules;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected FraudEvaluation() {
        // for JPA
    }

    public FraudEvaluation(String evaluationId, String transactionId, String accountId, BigDecimal amount,
                           String merchantName, String country, int score, FraudDecision decision,
                           List<String> matchedRules, LocalDateTime createdAt) {
        this.evaluationId = evaluationId;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
        this.merchantName = merchantName;
        this.country = country;
        this.score = score;
        this.decision = decision;
        this.matchedRules = matchedRules == null || matchedRules.isEmpty()
                ? null
                : String.join(",", matchedRules);
        this.createdAt = createdAt;
    }

    public List<String> getMatchedRules() {
        if (matchedRules == null || matchedRules.isBlank()) {
            return List.of();
        }
        return Arrays.stream(matchedRules.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }

    public String getEvaluationId() {
        return evaluationId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getCountry() {
        return country;
    }

    public int getScore() {
        return score;
    }

    public FraudDecision getDecision() {
        return decision;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
