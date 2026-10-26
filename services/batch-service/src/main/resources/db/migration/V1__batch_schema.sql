-- A1.6 批处理：作业定义与作业运行
-- 契约见 docs/design/14-risk-credit-collections.md §7.1

CREATE TABLE job_definition (
    job_code       VARCHAR(32)  PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    target_service VARCHAR(32)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE job_run (
    run_id        VARCHAR(36) PRIMARY KEY,
    job_code      VARCHAR(32) NOT NULL,
    business_date DATE        NOT NULL,
    status        VARCHAR(16) NOT NULL,
    started_at    TIMESTAMP   NOT NULL,
    completed_at  TIMESTAMP,
    message       VARCHAR(500),
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_job_run_definition FOREIGN KEY (job_code) REFERENCES job_definition (job_code)
);

CREATE INDEX idx_job_run_job_code ON job_run (job_code);
CREATE INDEX idx_job_run_business_date ON job_run (business_date);
CREATE INDEX idx_job_run_started_at ON job_run (started_at);
