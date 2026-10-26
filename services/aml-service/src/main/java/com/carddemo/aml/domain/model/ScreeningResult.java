package com.carddemo.aml.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 筛查结果（不可变记录）。matched_entry_id 仅在命中时非空。
 */
@Entity
@Table(name = "screening_result")
public class ScreeningResult {

    @Id
    @Column(name = "screening_id", length = 36, nullable = false, updatable = false)
    private String screeningId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "country", length = 3)
    private String country;

    @Column(name = "entity_type", length = 24)
    private String entityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20, nullable = false)
    private ScreeningResultStatus result;

    @Column(name = "matched_entry_id", length = 36)
    private String matchedEntryId;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ScreeningResult() {
        // for JPA
    }

    public ScreeningResult(String screeningId, String name, String country, String entityType,
                           ScreeningResultStatus result, String matchedEntryId, int score,
                           LocalDateTime createdAt) {
        this.screeningId = screeningId;
        this.name = name;
        this.country = country;
        this.entityType = entityType;
        this.result = result;
        this.matchedEntryId = matchedEntryId;
        this.score = score;
        this.createdAt = createdAt;
    }

    public boolean isHit() {
        return result != ScreeningResultStatus.CLEAR;
    }

    public String getScreeningId() {
        return screeningId;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public String getEntityType() {
        return entityType;
    }

    public ScreeningResultStatus getResult() {
        return result;
    }

    public String getMatchedEntryId() {
        return matchedEntryId;
    }

    public int getScore() {
        return score;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
