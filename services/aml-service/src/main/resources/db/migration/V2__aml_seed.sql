-- Seed watchlist, aligned with docs/design/14-risk-credit-collections.md §4.2: ACME FRAUD LTD (KP), BLOCKED TRADING (IR), IVAN PETROV (RU).
INSERT INTO sanctions_entry (entry_id, name, normalized_name, country, entity_type, list_source, active, version) VALUES
    ('sanctions-acme-fraud-ltd', 'ACME FRAUD LTD', 'ACME FRAUD LTD', 'KP', 'ENTITY', 'OFAC', TRUE, 0),
    ('sanctions-blocked-trading', 'BLOCKED TRADING', 'BLOCKED TRADING', 'IR', 'ENTITY', 'OFAC', TRUE, 0),
    ('sanctions-ivan-petrov', 'IVAN PETROV', 'IVAN PETROV', 'RU', 'INDIVIDUAL', 'UN', TRUE, 0);
