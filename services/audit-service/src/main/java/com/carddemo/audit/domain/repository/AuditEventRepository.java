package com.carddemo.audit.domain.repository;

import com.carddemo.audit.domain.model.AuditEvent;

import java.util.Optional;

/**
 * 审计事件仓储：只有追加与读取，没有更新/删除方法。
 */
public interface AuditEventRepository {

    AuditEvent append(AuditEvent event);

    Optional<AuditEvent> findEvent(String eventId);

    PageResult<AuditEvent> search(String actor, String resourceType, String resourceId, int page, int size);
}
