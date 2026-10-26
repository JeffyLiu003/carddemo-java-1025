package com.carddemo.audit.domain.model;

/**
 * 追加审计事件的输入。payload 为原始 JSON 文本，可为空。
 */
public record AuditEventDraft(String actor, String action, String resourceType,
                              String resourceId, String payload) {
}
