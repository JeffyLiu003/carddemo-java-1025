-- Payment instruction table. Contract: docs/design/13-payments-billing-limits-fx.md §2.1
-- status: RECEIVED / SCREENED / ACCEPTED / SETTLED / RETURNED / REJECTED
-- payment_type: ACH / SEPA / WIRE / INSTANT

CREATE TABLE payment_order (
    payment_id              VARCHAR(36)    PRIMARY KEY,
    idempotency_key         VARCHAR(80)    NOT NULL,
    debtor_account_id       VARCHAR(11)    NOT NULL,
    creditor_name           VARCHAR(100),
    creditor_account_number VARCHAR(34),
    creditor_bank_code      VARCHAR(11),
    amount                  NUMERIC(12, 2) NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    payment_type            VARCHAR(10)    NOT NULL,
    status                  VARCHAR(10)    NOT NULL,
    reference               VARCHAR(60),
    description             VARCHAR(200),
    transaction_batch_id    VARCHAR(36),
    reject_reason           VARCHAR(200),
    created_at              TIMESTAMP      NOT NULL,
    updated_at              TIMESTAMP      NOT NULL,
    settled_at              TIMESTAMP,
    returned_at             TIMESTAMP,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_order_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_payment_order_account ON payment_order (debtor_account_id);
CREATE INDEX idx_payment_order_status ON payment_order (status);
CREATE INDEX idx_payment_order_created ON payment_order (created_at);
