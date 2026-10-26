-- 13.3 账单：账单主表（唯一 (account_id, period) 保证同账户+期间只生成一张）
CREATE TABLE statement (
    statement_id    VARCHAR(36)    PRIMARY KEY,
    account_id      VARCHAR(11)    NOT NULL,
    period          VARCHAR(7)     NOT NULL,
    cycle_date      DATE           NOT NULL,
    opening_balance NUMERIC(14, 2) NOT NULL,
    closing_balance NUMERIC(14, 2) NOT NULL,
    purchases       NUMERIC(14, 2) NOT NULL,
    payments        NUMERIC(14, 2) NOT NULL,
    fees            NUMERIC(14, 2) NOT NULL,
    interest        NUMERIC(14, 2) NOT NULL,
    minimum_payment NUMERIC(14, 2) NOT NULL,
    due_date        DATE           NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    generated_at    TIMESTAMP      NOT NULL,
    paid_at         TIMESTAMP,
    version         BIGINT         NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_statement_account_period ON statement (account_id, period);
CREATE INDEX idx_statement_due_date ON statement (due_date);
CREATE INDEX idx_statement_status ON statement (status);
