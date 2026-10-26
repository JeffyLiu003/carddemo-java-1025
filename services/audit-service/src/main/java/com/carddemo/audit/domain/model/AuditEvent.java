package com.carddemo.audit.domain.model;

import com.carddemo.audit.domain.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 审计事件聚合。append-only：所有列 updatable = false，聚合没有可修改状态的方法，
 * 也不存在 repository 的 update/delete 语义（见 docs/design/15-operations-support.md §6）。
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private String eventId;

    @Column(name = "actor", length = 64, nullable = false, updatable = false)
    private String actor;

    @Column(name = "action", length = 80, nullable = false, updatable = false)
    private String action;

    @Column(name = "resource_type", length = 40, nullable = false, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 64, nullable = false, updatable = false)
    private String resourceId;

    /**
     * 原始 JSON 文本。DDL 使用 TEXT；PostgreSQL 的 TEXT 在 JDBC 元数据中表现为 VARCHAR，
     * 因此映射为不限长度字符串（不做 @Lob，避免 CLOB 与 TEXT 的类型校验冲突）。
     */
    @Column(name = "payload", length = 1_000_000, updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(String eventId, AuditEventDraft draft, LocalDateTime createdAt) {
        this.eventId = requireText(eventId, "eventId", 36);
        if (draft == null) {
            throw new InvalidRequestException("VALIDATION_FAILED", "Audit event body must be supplied.");
        }
        this.actor = requireText(draft.actor(), "actor", 64);
        this.action = requireText(draft.action(), "action", 80);
        this.resourceType = requireText(draft.resourceType(), "resourceType", 40);
        this.resourceId = requireText(draft.resourceId(), "resourceId", 64);
        this.payload = blankToNull(draft.payload());
        if (createdAt == null) {
            throw new InvalidRequestException("VALIDATION_FAILED", "createdAt must be supplied.");
        }
        this.createdAt = createdAt;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("VALIDATION_FAILED", field + " must be supplied.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new InvalidRequestException("VALIDATION_FAILED",
                    field + " must be " + maxLength + " characters or less.");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getEventId() {
        return eventId;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
