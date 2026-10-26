package com.carddemo.audit.interfaces.rest.dto;

import com.carddemo.audit.domain.model.AuditEvent;
import com.carddemo.audit.domain.repository.PageResult;

import java.util.List;

public record AuditEventPageResponse(List<AuditEventResponse> items, int page, int size, long total) {

    public static AuditEventPageResponse from(PageResult<AuditEvent> page) {
        return new AuditEventPageResponse(page.items().stream().map(AuditEventResponse::from).toList(),
                page.page(), page.size(), page.total());
    }
}
