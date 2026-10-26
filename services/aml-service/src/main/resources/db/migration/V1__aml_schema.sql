-- AML 名单筛查表结构，对齐 docs/design/14-risk-credit-collections.md §4.1

-- 制裁/名单条目：normalized_name 用于匹配，避免每次筛查重复归一化。
CREATE TABLE sanctions_entry (
    entry_id        VARCHAR(36)  PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    country         VARCHAR(3),
    entity_type     VARCHAR(24),
    list_source     VARCHAR(32),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_sanctions_entry_normalized ON sanctions_entry (normalized_name);

-- 筛查结果（不可变记录）：CLEAR/POTENTIAL_MATCH/BLOCKED。
CREATE TABLE screening_result (
    screening_id     VARCHAR(36)  PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    country          VARCHAR(3),
    entity_type      VARCHAR(24),
    result           VARCHAR(20)  NOT NULL,
    matched_entry_id VARCHAR(36),
    score            INTEGER      NOT NULL,
    created_at       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_screening_result_result ON screening_result (result, created_at);

-- 人工处置队列：POTENTIAL_MATCH/BLOCKED 生成 OPEN 警报。
CREATE TABLE aml_alert (
    alert_id     VARCHAR(36)  PRIMARY KEY,
    screening_id VARCHAR(36)  NOT NULL,
    customer_id  VARCHAR(9),
    status       VARCHAR(16)  NOT NULL,
    details      VARCHAR(300),
    created_at   TIMESTAMP    NOT NULL,
    resolved_at  TIMESTAMP,
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_aml_alert_status ON aml_alert (status, created_at);
