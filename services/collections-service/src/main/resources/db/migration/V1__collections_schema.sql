-- A1.5 Collections: delinquency cases, bucket and promise to pay
-- Contract: docs/design/14-risk-credit-collections.md §6.1

CREATE TABLE delinquency_case (
    case_id            VARCHAR(36)    PRIMARY KEY,
    account_id         VARCHAR(11)    NOT NULL,
    days_past_due      INTEGER        NOT NULL,
    bucket             VARCHAR(2)     NOT NULL,
    outstanding_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status             VARCHAR(16)    NOT NULL,
    promise_date       DATE,
    created_at         TIMESTAMP      NOT NULL,
    updated_at         TIMESTAMP      NOT NULL,
    resolved_at        TIMESTAMP,
    version            BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_delinquency_case_account ON delinquency_case (account_id);
CREATE INDEX idx_delinquency_case_status ON delinquency_case (status);
CREATE INDEX idx_delinquency_case_bucket ON delinquency_case (bucket);
