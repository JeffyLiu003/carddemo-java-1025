-- AML watchlist screening schema, aligned with docs/design/14-risk-credit-collections.md §4.1

-- Sanctions/watchlist entries: normalized_name is stored for matching so each screening does not re-normalize the text.
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

-- Screening results (immutable records): CLEAR/POTENTIAL_MATCH/BLOCKED.
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

-- Manual disposition queue: POTENTIAL_MATCH/BLOCKED produce OPEN alerts.
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
