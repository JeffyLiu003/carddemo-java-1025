-- Loan context schema; contract in docs/design/14-risk-credit-collections.md §2.1
-- loan_application.status: SUBMITTED / APPROVED / REJECTED / DISBURSED / CLOSED
-- loan.status: ACTIVE / CLOSED
-- repayment_schedule.status: PENDING / PAID

CREATE TABLE loan_application (
    application_id VARCHAR(36)    PRIMARY KEY,
    customer_id    VARCHAR(20)    NOT NULL,
    product_code   VARCHAR(20)    NOT NULL,
    principal      NUMERIC(12, 2) NOT NULL,
    term_months    INTEGER        NOT NULL,
    annual_rate    NUMERIC(6, 4)  NOT NULL,
    status         VARCHAR(16)    NOT NULL,
    reject_reason  VARCHAR(200),
    created_at     TIMESTAMP      NOT NULL,
    decided_at     TIMESTAMP,
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_loan_application_customer ON loan_application (customer_id);
CREATE INDEX idx_loan_application_status ON loan_application (status);
CREATE INDEX idx_loan_application_created ON loan_application (created_at);

CREATE TABLE loan (
    loan_id               VARCHAR(36)    PRIMARY KEY,
    application_id        VARCHAR(36)    NOT NULL,
    customer_id           VARCHAR(20)    NOT NULL,
    principal             NUMERIC(12, 2) NOT NULL,
    outstanding_principal NUMERIC(12, 2) NOT NULL,
    term_months           INTEGER        NOT NULL,
    annual_rate           NUMERIC(6, 4)  NOT NULL,
    status                VARCHAR(16)    NOT NULL,
    disbursed_at          TIMESTAMP,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_loan_application UNIQUE (application_id)
);

CREATE INDEX idx_loan_customer ON loan (customer_id);
CREATE INDEX idx_loan_status ON loan (status);

CREATE TABLE repayment_schedule (
    installment_id VARCHAR(36)    PRIMARY KEY,
    loan_id        VARCHAR(36)    NOT NULL,
    installment_no INTEGER        NOT NULL,
    due_date       DATE           NOT NULL,
    principal_due  NUMERIC(12, 2) NOT NULL,
    interest_due   NUMERIC(12, 2) NOT NULL,
    total_due      NUMERIC(12, 2) NOT NULL,
    status         VARCHAR(10)    NOT NULL,
    paid_at        TIMESTAMP,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_repayment_schedule_installment UNIQUE (loan_id, installment_no)
);

CREATE INDEX idx_repayment_schedule_loan ON repayment_schedule (loan_id, status);
