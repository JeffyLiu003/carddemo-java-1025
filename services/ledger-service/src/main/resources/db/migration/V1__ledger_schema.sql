-- A1.3 总账：科目表、会计期间、日记账
CREATE TABLE ledger_account (
    account_code VARCHAR(20) PRIMARY KEY,
    name         VARCHAR(80) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    parent_code  VARCHAR(20),
    currency     VARCHAR(3)  NOT NULL,
    active       BOOLEAN     NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE accounting_period (
    period    VARCHAR(7) PRIMARY KEY,
    status    VARCHAR(16) NOT NULL,
    closed_at TIMESTAMP,
    version   BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE journal_entry (
    entry_id    VARCHAR(36)  PRIMARY KEY,
    source_type VARCHAR(20)  NOT NULL,
    source_id   VARCHAR(64)  NOT NULL,
    description VARCHAR(255),
    posted_at   TIMESTAMP    NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    reversal_of VARCHAR(36),
    created_at  TIMESTAMP    NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_journal_entry_source ON journal_entry (source_type, source_id);
CREATE INDEX idx_journal_entry_posted_at ON journal_entry (posted_at);
CREATE INDEX idx_journal_entry_reversal_of ON journal_entry (reversal_of);

CREATE TABLE journal_line (
    line_id      VARCHAR(36)    PRIMARY KEY,
    entry_id     VARCHAR(36)    NOT NULL,
    line_no      INTEGER        NOT NULL,
    account_code VARCHAR(20)    NOT NULL,
    direction    VARCHAR(2)     NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_journal_line_entry FOREIGN KEY (entry_id) REFERENCES journal_entry (entry_id)
);

CREATE UNIQUE INDEX uk_journal_line_no ON journal_line (entry_id, line_no);
CREATE INDEX idx_journal_line_account ON journal_line (account_code);
