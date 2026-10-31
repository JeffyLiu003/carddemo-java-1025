-- Workflow context schema; contract in docs/design/15-operations-support.md §5.1
-- workflow_task.status: PENDING / APPROVED / REJECTED

CREATE TABLE workflow_definition (
    definition_code VARCHAR(40)  PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    steps           VARCHAR(500) NOT NULL,
    active          BOOLEAN      NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE workflow_task (
    task_id         VARCHAR(36)  PRIMARY KEY,
    definition_code VARCHAR(40)  NOT NULL,
    business_ref    VARCHAR(64)  NOT NULL,
    assignee        VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    comment         VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL,
    decided_at      TIMESTAMP,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_workflow_task_status ON workflow_task (status);
CREATE INDEX idx_workflow_task_assignee ON workflow_task (assignee);
CREATE INDEX idx_workflow_task_definition ON workflow_task (definition_code);
CREATE INDEX idx_workflow_task_created ON workflow_task (created_at);
