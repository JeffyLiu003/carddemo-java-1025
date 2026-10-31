-- 17.4 Marketing seed: two premium card term cashback campaigns (examples in docs/design/17-premium-cashback-campaign.md §4)
-- end_date = start_date + duration_months - 1 day (PC-01); budget and caps are the values given in the design (PC-02).
INSERT INTO campaign (campaign_id, code, name, description, status, start_date, duration_months, end_date,
                      eligible_product_codes, min_transaction_amount, cashback_rate,
                      max_cashback_per_transaction, max_cashback_per_customer, budget, consumed_budget,
                      enrollment_required, created_at, updated_at, version)
VALUES ('a1000000-0000-4000-8000-000000000001', 'PLATINUM_3M_CASHBACK', 'Platinum 3-month purchase cashback',
        '5% cashback on purchases during the campaign, capped at 30 per transaction and 300 in total', 'ACTIVE', DATE '2026-10-01', 3, DATE '2026-12-31',
        'PLATINUM', 50.00, 0.050000, 30.00, 300.00, 50000.00, 0.00,
        TRUE, TIMESTAMP '2026-10-01 00:00:00', TIMESTAMP '2026-10-01 00:00:00', 0),
       ('a1000000-0000-4000-8000-000000000002', 'GOLD_6M_CASHBACK', 'Gold 6-month purchase cashback',
        '3% cashback on purchases during the campaign, capped at 20 per transaction and 120 in total', 'ACTIVE', DATE '2026-10-01', 6, DATE '2027-03-31',
        'GOLD', 30.00, 0.030000, 20.00, 120.00, 20000.00, 0.00,
        TRUE, TIMESTAMP '2026-10-01 00:00:00', TIMESTAMP '2026-10-01 00:00:00', 0);
