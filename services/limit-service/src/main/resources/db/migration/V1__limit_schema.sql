-- P2 Limits: customer/account/channel limits and usage, aligned with docs/design/13-payments-billing-limits-fx.md §4.1
CREATE TABLE limit_definition (
    limit_id    VARCHAR(36)    PRIMARY KEY,
    scope_type  VARCHAR(16)    NOT NULL,
    scope_id    VARCHAR(64)    NOT NULL,
    limit_type  VARCHAR(16)    NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    amount      NUMERIC(16, 2) NOT NULL,
    used_amount NUMERIC(16, 2) NOT NULL,
    active      BOOLEAN        NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_limit_definition_scope
    ON limit_definition (scope_type, scope_id, limit_type, currency);
CREATE INDEX idx_limit_definition_scope ON limit_definition (scope_type, scope_id);
