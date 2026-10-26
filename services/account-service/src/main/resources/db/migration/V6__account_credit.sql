-- 通用账户贷记（返现冲减欠款等），credit_id 作为幂等键（docs/design/17-premium-cashback-campaign.md §3.2）
CREATE TABLE account_credit (
    credit_id   VARCHAR(36) PRIMARY KEY,
    account_id  VARCHAR(11)    NOT NULL,
    source_type VARCHAR(30)    NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    description VARCHAR(200),
    created_at  TIMESTAMP      NOT NULL
);

CREATE INDEX idx_account_credit_account ON account_credit (account_id);
