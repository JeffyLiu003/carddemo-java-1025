-- Workflow definition seed (docs/design/15-operations-support.md §5.1)

INSERT INTO workflow_definition (definition_code, name, steps, active, version) VALUES
    ('LIMIT_INCREASE', 'Limit increase approval', 'REQUEST,REVIEW,APPROVE', TRUE, 0),
    ('PAYMENT_REVIEW', 'Payment manual review', 'REQUEST,REVIEW', TRUE, 0),
    ('DISPUTE_REVIEW', 'Dispute review', 'REVIEW,DECIDE', TRUE, 0);
