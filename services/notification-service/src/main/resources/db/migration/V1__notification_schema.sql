-- P4 运营支撑：通知模板 / 通知
-- 契约见 docs/design/15-operations-support.md §3.1

CREATE TABLE notification_template (
    template_code VARCHAR(40)   PRIMARY KEY,
    channel       VARCHAR(16)   NOT NULL,
    subject       VARCHAR(200),
    body          VARCHAR(2000) NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    version       BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE notification (
    notification_id VARCHAR(36)   PRIMARY KEY,
    template_code   VARCHAR(40)   NOT NULL,
    recipient       VARCHAR(200)  NOT NULL,
    channel         VARCHAR(16)   NOT NULL,
    payload         VARCHAR(4000),
    status          VARCHAR(16)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    sent_at         TIMESTAMP,
    error           VARCHAR(500),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_notification_template FOREIGN KEY (template_code)
        REFERENCES notification_template (template_code)
);

CREATE INDEX idx_notification_status ON notification (status);
CREATE INDEX idx_notification_created_at ON notification (created_at);
