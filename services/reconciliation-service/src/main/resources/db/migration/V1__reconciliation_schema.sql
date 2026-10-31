-- A1.4 Reconciliation and EOD: reconciliation runs / items / EOD runs
-- Contract: docs/design/12-reconciliation-and-eod.md §3

CREATE TABLE reconciliation_run (
    run_id                VARCHAR(36)   PRIMARY KEY,
    business_date         DATE          NOT NULL,
    period                VARCHAR(7)    NOT NULL,
    status                VARCHAR(16)   NOT NULL,
    started_at            TIMESTAMP     NOT NULL,
    completed_at          TIMESTAMP,
    batch_count           INTEGER       NOT NULL DEFAULT 0,
    batch_amount          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    journal_count         INTEGER       NOT NULL DEFAULT 0,
    journal_debit_total   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    journal_credit_total  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    match_count           INTEGER       NOT NULL DEFAULT 0,
    mismatch_count        INTEGER       NOT NULL DEFAULT 0,
    missing_journal_count INTEGER       NOT NULL DEFAULT 0,
    missing_batch_count   INTEGER       NOT NULL DEFAULT 0,
    matched               BOOLEAN       NOT NULL DEFAULT FALSE,
    error_message         VARCHAR(500),
    version               BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_reconciliation_run_business_date ON reconciliation_run (business_date);
CREATE INDEX idx_reconciliation_run_started_at ON reconciliation_run (started_at);

CREATE TABLE reconciliation_item (
    item_id         VARCHAR(36)    PRIMARY KEY,
    run_id          VARCHAR(36)    NOT NULL,
    check_type      VARCHAR(24)    NOT NULL,
    reference_id    VARCHAR(64)    NOT NULL,
    expected_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    actual_amount   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    difference      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status          VARCHAR(24)    NOT NULL,
    resolved        BOOLEAN        NOT NULL DEFAULT FALSE,
    resolution_note VARCHAR(500),
    detail          VARCHAR(500),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_reconciliation_item_run FOREIGN KEY (run_id) REFERENCES reconciliation_run (run_id)
);

CREATE INDEX idx_reconciliation_item_run ON reconciliation_item (run_id);

CREATE TABLE eod_run (
    run_id                VARCHAR(36) PRIMARY KEY,
    business_date         DATE        NOT NULL,
    status                VARCHAR(16) NOT NULL,
    reconciliation_run_id VARCHAR(36),
    started_at            TIMESTAMP   NOT NULL,
    completed_at          TIMESTAMP,
    message               VARCHAR(500),
    version               BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_eod_run_business_date ON eod_run (business_date);
CREATE INDEX idx_eod_run_started_at ON eod_run (started_at);
