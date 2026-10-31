-- P2 FX: rates, quotes and conversion records, aligned with docs/design/13-payments-billing-limits-fx.md §5.1
CREATE TABLE fx_rate (
    pair_id        VARCHAR(36)    PRIMARY KEY,
    base_currency  VARCHAR(3)     NOT NULL,
    quote_currency VARCHAR(3)     NOT NULL,
    rate           NUMERIC(12, 6) NOT NULL,
    effective_at   TIMESTAMP      NOT NULL,
    active         BOOLEAN        NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_fx_rate_pair ON fx_rate (base_currency, quote_currency);

CREATE TABLE fx_quote (
    quote_id         VARCHAR(36)    PRIMARY KEY,
    from_currency    VARCHAR(3)     NOT NULL,
    to_currency      VARCHAR(3)     NOT NULL,
    source_amount    NUMERIC(16, 2) NOT NULL,
    rate             NUMERIC(12, 6) NOT NULL,
    converted_amount NUMERIC(16, 2) NOT NULL,
    created_at       TIMESTAMP      NOT NULL,
    expires_at       TIMESTAMP      NOT NULL,
    consumed         BOOLEAN        NOT NULL
);

CREATE INDEX idx_fx_quote_created_at ON fx_quote (created_at);

CREATE TABLE fx_conversion (
    conversion_id    VARCHAR(36)    PRIMARY KEY,
    quote_id         VARCHAR(36)    NOT NULL,
    from_currency    VARCHAR(3)     NOT NULL,
    to_currency      VARCHAR(3)     NOT NULL,
    source_amount    NUMERIC(16, 2) NOT NULL,
    rate             NUMERIC(12, 6) NOT NULL,
    converted_amount NUMERIC(16, 2) NOT NULL,
    created_at       TIMESTAMP      NOT NULL,
    CONSTRAINT fk_fx_conversion_quote FOREIGN KEY (quote_id) REFERENCES fx_quote (quote_id)
);

CREATE UNIQUE INDEX uk_fx_conversion_quote ON fx_conversion (quote_id);
CREATE INDEX idx_fx_conversion_created_at ON fx_conversion (created_at);
