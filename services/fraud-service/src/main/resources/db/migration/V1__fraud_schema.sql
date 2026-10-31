-- Fraud context schema, aligned with docs/design/14-risk-credit-collections.md §3.1

-- Scoring rules: the rule code is the primary key; AMOUNT_GT uses threshold_amount, the rest use match_value.
CREATE TABLE fraud_rule (
    rule_code        VARCHAR(32) PRIMARY KEY,
    name             VARCHAR(80)    NOT NULL,
    rule_type        VARCHAR(24)    NOT NULL,
    threshold_amount NUMERIC(12, 2),
    match_value      VARCHAR(100),
    score            INTEGER        NOT NULL,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    version          BIGINT         NOT NULL DEFAULT 0
);

-- Scoring results: matched_rules is a comma-separated list of rule codes, in rule execution order.
CREATE TABLE fraud_evaluation (
    evaluation_id  VARCHAR(36)    PRIMARY KEY,
    transaction_id VARCHAR(36)    NOT NULL,
    account_id     VARCHAR(11)    NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    merchant_name  VARCHAR(50),
    country        VARCHAR(3),
    score          INTEGER        NOT NULL,
    decision       VARCHAR(10)    NOT NULL,
    matched_rules  VARCHAR(200),
    created_at     TIMESTAMP      NOT NULL
);

CREATE INDEX idx_fraud_evaluation_account ON fraud_evaluation (account_id, created_at);
CREATE INDEX idx_fraud_evaluation_transaction ON fraud_evaluation (transaction_id);

-- Manual disposition queue: REVIEW/REJECT scores produce OPEN alerts.
CREATE TABLE fraud_alert (
    alert_id      VARCHAR(36) PRIMARY KEY,
    evaluation_id VARCHAR(36) NOT NULL,
    account_id    VARCHAR(11),
    score         INTEGER     NOT NULL,
    status        VARCHAR(16) NOT NULL,
    reason        VARCHAR(300),
    created_at    TIMESTAMP   NOT NULL,
    resolved_at   TIMESTAMP,
    version       BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_fraud_alert_status ON fraud_alert (status, created_at);
CREATE INDEX idx_fraud_alert_account ON fraud_alert (account_id, created_at);
