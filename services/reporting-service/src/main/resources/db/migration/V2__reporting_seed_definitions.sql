-- 种子报表定义：日交易汇总 → transaction-service、试算平衡 → ledger-service（设计 §2.2）

INSERT INTO report_definition (report_code, name, description, source_service, active, version) VALUES
    ('DAILY_TRANSACTION_SUMMARY', '日交易汇总',
     '按卡号汇总一个业务日内的交易金额，并追加一行 TOTAL', 'transaction', TRUE, 0),
    ('TRIAL_BALANCE', '试算平衡表',
     '按会计期间汇总每个总账科目的借贷发生额与净额', 'ledger', TRUE, 0);
