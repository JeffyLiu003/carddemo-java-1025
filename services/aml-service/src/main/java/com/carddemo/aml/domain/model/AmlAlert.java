package com.carddemo.aml.domain.model;

import com.carddemo.aml.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * AML 警报聚合。状态机：OPEN → CLEARED | ESCALATED，终态不可再变更。
 */
@Entity
@Table(name = "aml_alert")
public class AmlAlert {

    @Id
    @Column(name = "alert_id", length = 36, nullable = false, updatable = false)
    private String alertId;

    @Column(name = "screening_id", length = 36, nullable = false)
    private String screeningId;

    @Column(name = "customer_id", length = 9)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private AmlAlertStatus status;

    @Column(name = "details", length = 300)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AmlAlert() {
        // for JPA
    }

    private AmlAlert(String alertId, String screeningId, String customerId, String details,
                     LocalDateTime createdAt) {
        this.alertId = alertId;
        this.screeningId = screeningId;
        this.customerId = customerId;
        this.status = AmlAlertStatus.OPEN;
        this.details = details;
        this.createdAt = createdAt;
    }

    public static AmlAlert openFor(String alertId, ScreeningResult screening, String customerId,
                                   LocalDateTime createdAt) {
        String details = "Screening " + screening.getResult() + " score " + screening.getScore()
                + (screening.getMatchedEntryId() == null
                        ? ""
                        : " matched sanctions entry " + screening.getMatchedEntryId());
        return new AmlAlert(alertId, screening.getScreeningId(), customerId, details, createdAt);
    }

    public void clear(LocalDateTime resolvedAt) {
        transition(AmlAlertStatus.CLEARED, resolvedAt);
    }

    public void escalate(LocalDateTime resolvedAt) {
        transition(AmlAlertStatus.ESCALATED, resolvedAt);
    }

    private void transition(AmlAlertStatus target, LocalDateTime resolvedAt) {
        if (status != AmlAlertStatus.OPEN) {
            throw new BusinessRuleException("ALERT_ALREADY_RESOLVED",
                    "Alert " + alertId + " is already " + status + ".");
        }
        this.status = target;
        this.resolvedAt = resolvedAt;
    }

    public String getAlertId() {
        return alertId;
    }

    public String getScreeningId() {
        return screeningId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public AmlAlertStatus getStatus() {
        return status;
    }

    public String getDetails() {
        return details;
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
