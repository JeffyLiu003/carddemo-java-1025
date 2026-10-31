-- Dispute context schema; contract in docs/design/14-risk-credit-collections.md §5.1
-- status: OPEN / UNDER_REVIEW / PROVISIONAL_CREDIT / CHARGEBACK / WON / LOST / CLOSED

CREATE TABLE dispute_case (
    case_id                  VARCHAR(36)    PRIMARY KEY,
    transaction_id           VARCHAR(36)    NOT NULL,
    account_id               VARCHAR(11)    NOT NULL,
    customer_id              VARCHAR(20)    NOT NULL,
    reason_code              VARCHAR(30)    NOT NULL,
    amount                   NUMERIC(12, 2) NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    provisional_credit_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    opened_at                TIMESTAMP      NOT NULL,
    updated_at               TIMESTAMP      NOT NULL,
    closed_at                TIMESTAMP,
    version                  BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_dispute_case_account ON dispute_case (account_id);
CREATE INDEX idx_dispute_case_status ON dispute_case (status);
CREATE INDEX idx_dispute_case_transaction ON dispute_case (transaction_id);
CREATE INDEX idx_dispute_case_opened ON dispute_case (opened_at);
