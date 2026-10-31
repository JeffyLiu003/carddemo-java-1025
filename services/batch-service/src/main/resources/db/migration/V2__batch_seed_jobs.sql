-- Seed jobs: interest accrual → transaction-service, reconciliation → reconciliation-service (design §7.1)

INSERT INTO job_definition (job_code, name, description, target_service, active, version) VALUES
    ('INTEREST_RUN', 'Interest run',
     'EOD interest accrual: calls transaction-service to accrue revolving interest for all accounts', 'transaction', TRUE, 0),
    ('RECONCILIATION', 'Reconciliation run',
     'EOD reconciliation: calls reconciliation-service to compare the postings and general ledger for the business day', 'reconciliation', TRUE, 0);
