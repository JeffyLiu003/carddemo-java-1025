-- 18.1 白金卡额外 0.5% 消费返现活动种子（docs/design/18-platinum-extra-cashback.md）
-- 需求 REQ-f1537278-419c-4aac-a68d-0d73cf31d5c3：白金卡客户未来三个月消费额外 0.5% 返现。
-- end_date = start_date + duration_months - 1 天（PC-01）；费率为 0.005，无需报名。
INSERT INTO campaign (campaign_id, code, name, description, status, start_date, duration_months, end_date,
                      eligible_product_codes, min_transaction_amount, cashback_rate,
                      max_cashback_per_transaction, max_cashback_per_customer, budget, consumed_budget,
                      enrollment_required, created_at, updated_at, version)
VALUES ('a1000000-0000-4000-8000-000000000003', 'PLATINUM_EXTRA_05', '白金卡额外 0.5% 消费返现',
        '活动期内白金卡消费额外返现 0.5%，单笔最高 30，累计最高 300；无需报名', 'ACTIVE',
        DATE '2026-11-01', 3, DATE '2027-01-31', 'PLATINUM', 50.00, 0.005000, 30.00, 300.00, 50000.00, 0.00,
        FALSE, TIMESTAMP '2026-11-01 00:00:00', TIMESTAMP '2026-11-01 00:00:00', 0);
