-- REQ-01af1cdb-c7dd-4434-8cec-ac9547e12b79（REQ-2026-005）白金卡消费额外 0.5% 返现
-- 承载方式：独立活动与既有 PLATINUM_3M_CASHBACK（5%）并存，同一交易按各自活动分别评估与发放，
-- 因此白金卡客户在活动期内合计返现 5.5%。设计与决策见 docs/design/18-platinum-extra-cashback.md。
-- 参数口径：费率 0.005 与 3 个月期限来自原始需求；起止窗口固定为 2026-11-01 起 3 个月；
-- 门槛/单笔上限/客户上限/预算/报名沿用既有白金活动参数（开发者 2026-11-02 确认，PRD 12.3 节 Q1/Q4/Q5 的本次取值）。
INSERT INTO campaign (campaign_id, code, name, description, status, start_date, duration_months, end_date,
                      eligible_product_codes, min_transaction_amount, cashback_rate,
                      max_cashback_per_transaction, max_cashback_per_customer, budget, consumed_budget,
                      enrollment_required, created_at, updated_at, version)
VALUES ('a1000000-0000-4000-8000-000000000003', 'PLATINUM_EXTRA_0P5_CASHBACK', '白金卡消费额外 0.5% 返现',
        '活动期内白金卡消费在既有返现之上额外返现 0.5%，单笔最高 30，累计最高 300', 'ACTIVE',
        DATE '2026-11-01', 3, DATE '2027-01-31',
        'PLATINUM', 50.00, 0.005000, 30.00, 300.00, 50000.00, 0.00,
        TRUE, TIMESTAMP '2026-11-01 00:00:00', TIMESTAMP '2026-11-01 00:00:00', 0);
