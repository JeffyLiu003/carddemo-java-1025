package com.carddemo.audit.application;

import com.carddemo.audit.domain.exception.InvalidRequestException;
import com.carddemo.audit.domain.exception.NotFoundException;
import com.carddemo.audit.domain.model.AuditEvent;
import com.carddemo.audit.domain.model.AuditEventDraft;
import com.carddemo.audit.domain.repository.AuditEventRepository;
import com.carddemo.audit.domain.repository.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审计用例：只追加（AU-01）与检索（AU-02/03）。没有更新、删除用例。
 */
@Service
@Transactional
public class AuditApplicationService {

    static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditApplicationService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public AuditEvent append(AuditEventDraft draft) {
        AuditEvent event = new AuditEvent(UUID.randomUUID().toString(), draft, LocalDateTime.now(clock));
        return events.append(event);
    }

    @Transactional(readOnly = true)
    public AuditEvent get(String eventId) {
        return events.findEvent(eventId)
                .orElseThrow(() -> new NotFoundException("AUDIT_EVENT_NOT_FOUND",
                        "Audit event " + eventId + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<AuditEvent> search(String actor, String resourceType, String resourceId,
                                         int page, int size) {
        validatePage(page, size);
        return events.search(blankToNull(actor), blankToNull(resourceType), blankToNull(resourceId),
                page, size);
    }

    static void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("VALIDATION_FAILED", "Page must not be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("VALIDATION_FAILED",
                    "Size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
