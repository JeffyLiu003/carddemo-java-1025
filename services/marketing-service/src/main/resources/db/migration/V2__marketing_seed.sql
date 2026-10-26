-- 17.4 营销种子：两个高端卡期限返现活动（docs/design/17-premium-cashback-campaign.md §4 示例）
-- end_date = start_date + duration_months - 1 天（PC-01）；预算与上限按设计给定（PC-02）。
INSERT INTO campaign (campaign_id, code, name, description, status, start_date, duration_months, end_date,
                      eligible_product_codes, min_transaction_amount, cashback_rate,
                      max_cashback_per_transaction, max_cashback_per_customer, budget, consumed_budget,
                      enrollment_required, created_at, updated_at, version)
VALUES ('a1000000-0000-4000-8000-000000000001', 'PLATINUM_3M_CASHBACK', '白金卡 3 个月消费返现',
        '活动期内消费 5% 返现，单笔最高 30，累计最高 300', 'ACTIVE', DATE '2026-10-01', 3, DATE '2026-12-31',
        'PLATINUM', 50.00, 0.050000, 30.00, 300.00, 50000.00, 0.00,
        TRUE, TIMESTAMP '2026-10-01 00:00:00', TIMESTAMP '2026-10-01 00:00:00', 0),
       ('a1000000-0000-4000-8000-000000000002', 'GOLD_6M_CASHBACK', '金卡 6 个月消费返现',
        '活动期内消费 3% 返现，单笔最高 20，累计最高 120', 'ACTIVE', DATE '2026-10-01', 6, DATE '2027-03-31',
        'GOLD', 30.00, 0.030000, 20.00, 120.00, 20000.00, 0.00,
        TRUE, TIMESTAMP '2026-10-01 00:00:00', TIMESTAMP '2026-10-01 00:00:00', 0);
