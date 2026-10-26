-- 流程定义种子（docs/design/15-operations-support.md §5.1）

INSERT INTO workflow_definition (definition_code, name, steps, active, version) VALUES
    ('LIMIT_INCREASE', '额度提升审批', 'REQUEST,REVIEW,APPROVE', TRUE, 0),
    ('PAYMENT_REVIEW', '支付人工复核', 'REQUEST,REVIEW', TRUE, 0),
    ('DISPUTE_REVIEW', '争议复核', 'REVIEW,DECIDE', TRUE, 0);
