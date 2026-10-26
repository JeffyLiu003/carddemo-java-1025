-- 种子作业：计息 → transaction-service、对账 → reconciliation-service（设计 §7.1）

INSERT INTO job_definition (job_code, name, description, target_service, active, version) VALUES
    ('INTEREST_RUN', '计息作业',
     'EOD 计息：调用 transaction-service 对全部账户计提循环利息', 'transaction', TRUE, 0),
    ('RECONCILIATION', '对账作业',
     'EOD 对账：调用 reconciliation-service 执行当日过账与总账对账', 'reconciliation', TRUE, 0);
