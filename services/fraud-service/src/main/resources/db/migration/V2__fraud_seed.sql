-- Seed rules, aligned with docs/design/14-risk-credit-collections.md §3.2: amount > 5000 (60), merchant BLOCKED MERCHANT (100), country KP/IR (80).
INSERT INTO fraud_rule (rule_code, name, rule_type, threshold_amount, match_value, score, active, version) VALUES
    ('AMOUNT_GT', 'Large amount transaction', 'AMOUNT_GT', 5000.00, NULL, 60, TRUE, 0),
    ('MERCHANT_BLOCKED', 'Blocked merchant', 'MERCHANT_BLOCKED', NULL, 'BLOCKED MERCHANT', 100, TRUE, 0),
    ('COUNTRY_BLOCKED', 'Blocked country', 'COUNTRY_BLOCKED', NULL, 'KP,IR', 80, TRUE, 0);
