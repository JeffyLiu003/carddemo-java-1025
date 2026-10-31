-- Seed report definitions: daily transaction summary → transaction-service, trial balance → ledger-service (design §2.2)

INSERT INTO report_definition (report_code, name, description, source_service, active, version) VALUES
    ('DAILY_TRANSACTION_SUMMARY', 'Daily transaction summary',
     'Sum transaction amounts per card number within one business day and append a TOTAL row', 'transaction', TRUE, 0),
    ('TRIAL_BALANCE', 'Trial balance',
     'Summarize debit and credit activity plus the net amount for each general ledger account in an accounting period', 'ledger', TRUE, 0);
