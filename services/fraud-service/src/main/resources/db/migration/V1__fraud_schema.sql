-- 欺诈上下文表结构，对齐 docs/design/14-risk-credit-collections.md §3.1

-- 评分规则：规则编码即主键，AMOUNT_GT 用 threshold_amount，其余用 match_value。
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

-- 评分结果：matched_rules 以逗号分隔规则编码，保持与规则执行顺序一致。
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

-- 人工处置队列：REVIEW/REJECT 评分生成 OPEN 警报。
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
