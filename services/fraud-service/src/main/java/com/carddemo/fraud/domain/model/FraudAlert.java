package com.carddemo.fraud.domain.model;

import com.carddemo.fraud.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * 欺诈警报聚合。状态机：OPEN → CONFIRMED | DISMISSED，终态不可再变更。
 */
@Entity
@Table(name = "fraud_alert")
public class FraudAlert {

    @Id
    @Column(name = "alert_id", length = 36, nullable = false, updatable = false)
    private String alertId;

    @Column(name = "evaluation_id", length = 36, nullable = false)
    private String evaluationId;

    @Column(name = "account_id", length = 11)
    private String accountId;

    @Column(name = "score", nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private FraudAlertStatus status;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FraudAlert() {
        // for JPA
    }

    private FraudAlert(String alertId, String evaluationId, String accountId, int score,
                       String reason, LocalDateTime createdAt) {
        this.alertId = alertId;
        this.evaluationId = evaluationId;
        this.accountId = accountId;
        this.score = score;
        this.status = FraudAlertStatus.OPEN;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public static FraudAlert openFor(String alertId, FraudEvaluation evaluation, LocalDateTime createdAt) {
        String reason = "Decision " + evaluation.getDecision() + " score " + evaluation.getScore()
                + "; matched rules: " + String.join(", ", evaluation.getMatchedRules());
        return new FraudAlert(alertId, evaluation.getEvaluationId(), evaluation.getAccountId(),
                evaluation.getScore(), reason, createdAt);
    }

    public void confirm(LocalDateTime resolvedAt) {
        transition(FraudAlertStatus.CONFIRMED, resolvedAt);
    }

    public void dismiss(LocalDateTime resolvedAt) {
        transition(FraudAlertStatus.DISMISSED, resolvedAt);
    }

    private void transition(FraudAlertStatus target, LocalDateTime resolvedAt) {
        if (status != FraudAlertStatus.OPEN) {
            throw new BusinessRuleException("ALERT_ALREADY_RESOLVED",
                    "Alert " + alertId + " is already " + status + ".");
        }
        this.status = target;
        this.resolvedAt = resolvedAt;
    }

    public String getAlertId() {
        return alertId;
    }

    public String getEvaluationId() {
        return evaluationId;
    }

    public String getAccountId() {
        return accountId;
    }

    public int getScore() {
        return score;
    }

    public FraudAlertStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public long getVersion() {
        return version;
    }
}
