-- A1.1 Account productization and funds hold
CREATE TABLE account_product (
    product_code          VARCHAR(20) PRIMARY KEY,
    name                  VARCHAR(60)    NOT NULL,
    category              VARCHAR(20)    NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    default_credit_limit  NUMERIC(12, 2) NOT NULL,
    max_credit_limit      NUMERIC(12, 2) NOT NULL,
    default_cash_limit    NUMERIC(12, 2) NOT NULL,
    max_cash_limit        NUMERIC(12, 2) NOT NULL,
    apr                   NUMERIC(6, 4)  NOT NULL,
    monthly_fee           NUMERIC(12, 2) NOT NULL,
    statement_cycle_day   INTEGER        NOT NULL,
    active                BOOLEAN        NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0
);

INSERT INTO account_product (product_code, name, category, currency,
                             default_credit_limit, max_credit_limit,
                             default_cash_limit, max_cash_limit,
                             apr, monthly_fee, statement_cycle_day, active, version)
VALUES ('CLASSIC', 'Classic Card', 'CREDIT', 'USD', 5000.00, 5000.00, 2000.00, 2000.00,
        0.1899, 0.00, 25, TRUE, 0),
       ('GOLD', 'Gold Card', 'CREDIT', 'USD', 10000.00, 10000.00, 4000.00, 4000.00,
        0.1699, 95.00, 15, TRUE, 0),
       ('PLATINUM', 'Platinum Card', 'CREDIT', 'USD', 25000.00, 25000.00, 10000.00, 10000.00,
        0.1499, 295.00, 5, TRUE, 0);

ALTER TABLE account ADD COLUMN product_code VARCHAR(20) NOT NULL DEFAULT 'CLASSIC';

UPDATE account
SET product_code = CASE
    WHEN credit_limit >= 8000.00 THEN 'PLATINUM'
    WHEN credit_limit >= 5000.00 THEN 'GOLD'
    ELSE 'CLASSIC'
END;

CREATE INDEX idx_account_product ON account (product_code);

CREATE TABLE account_hold (
    hold_id        VARCHAR(36) PRIMARY KEY,
    account_id     VARCHAR(11)    NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    status         VARCHAR(16)    NOT NULL,
    reference_type VARCHAR(20)    NOT NULL,
    reference_id   VARCHAR(64)    NOT NULL,
    created_at     TIMESTAMP      NOT NULL,
    expires_at     TIMESTAMP,
    released_at    TIMESTAMP,
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_account_hold_reference ON account_hold (account_id, reference_id);
CREATE INDEX idx_account_hold_account_status ON account_hold (account_id, status);
