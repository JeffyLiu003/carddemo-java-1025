-- 审计事件：只追加（应用层不提供 UPDATE/DELETE），payload 保存原始 JSON 文本
CREATE TABLE audit_event (
    event_id      VARCHAR(36) PRIMARY KEY,
    actor         VARCHAR(64) NOT NULL,
    action        VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id   VARCHAR(64) NOT NULL,
    payload       TEXT,
    created_at    TIMESTAMP   NOT NULL
);

CREATE INDEX idx_audit_event_actor_created ON audit_event (actor, created_at);
CREATE INDEX idx_audit_event_resource_created ON audit_event (resource_type, resource_id, created_at);
CREATE INDEX idx_audit_event_created ON audit_event (created_at);
