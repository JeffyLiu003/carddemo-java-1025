-- Initial rate seed, aligned with docs/design/13-payments-billing-limits-fx.md §5.2
INSERT INTO fx_rate (pair_id, base_currency, quote_currency, rate, effective_at, active, version) VALUES
    ('fx-rate-usd-cny', 'USD', 'CNY', 7.100000, TIMESTAMP '2026-10-01 00:00:00', TRUE, 0),
    ('fx-rate-usd-eur', 'USD', 'EUR', 0.920000, TIMESTAMP '2026-10-01 00:00:00', TRUE, 0),
    ('fx-rate-eur-cny', 'EUR', 'CNY', 7.720000, TIMESTAMP '2026-10-01 00:00:00', TRUE, 0);
