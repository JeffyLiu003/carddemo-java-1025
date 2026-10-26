package com.carddemo.audit.interfaces.rest.dto;

import com.carddemo.audit.domain.model.AuditEvent;

import java.time.LocalDateTime;

public record AuditEventResponse(
        String eventId,
        String actor,
        String action,
        String resourceType,
        String resourceId,
        String payload,
        LocalDateTime createdAt) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getEventId(), event.getActor(), event.getAction(),
                event.getResourceType(), event.getResourceId(), event.getPayload(),
                event.getCreatedAt());
    }
}
