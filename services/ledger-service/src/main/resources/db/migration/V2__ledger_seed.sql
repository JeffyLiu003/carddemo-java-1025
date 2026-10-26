-- 科目表（COA）与会计期间种子，对齐 docs/design/11-money-path-slice-a2.md §3.2
INSERT INTO ledger_account (account_code, name, account_type, parent_code, currency, active, version) VALUES
    ('1000', 'Cash',               'ASSET',     NULL, 'USD', TRUE, 0),
    ('1100', 'Card Receivables',   'ASSET',     NULL, 'USD', TRUE, 0),
    ('2000', 'Customer Deposits',  'LIABILITY', NULL, 'USD', TRUE, 0),
    ('2100', 'Merchant Payable',   'LIABILITY', NULL, 'USD', TRUE, 0),
    ('2200', 'Rewards Payable',    'LIABILITY', NULL, 'USD', TRUE, 0),
    ('4000', 'Fee Income',         'REVENUE',   NULL, 'USD', TRUE, 0),
    ('4100', 'Interest Income',    'REVENUE',   NULL, 'USD', TRUE, 0),
    ('5000', 'Interest Expense',   'EXPENSE',   NULL, 'USD', TRUE, 0),
    ('9000', 'Suspense',           'ASSET',     NULL, 'USD', TRUE, 0);

INSERT INTO accounting_period (period, status, closed_at, version) VALUES
    ('2026-09', 'CLOSED', TIMESTAMP '2026-10-01 00:00:00', 0),
    ('2026-10', 'OPEN',   NULL, 0),
    ('2026-11', 'OPEN',   NULL, 0);
