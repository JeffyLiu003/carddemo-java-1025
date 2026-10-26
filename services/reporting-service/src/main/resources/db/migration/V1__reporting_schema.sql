-- P4 运营支撑：报表定义 / 报表运行 / 报表结果行
-- 契约见 docs/design/15-operations-support.md §2.1

CREATE TABLE report_definition (
    report_code    VARCHAR(40)  PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    source_service VARCHAR(32)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE report_run (
    run_id        VARCHAR(36)  PRIMARY KEY,
    report_code   VARCHAR(40)  NOT NULL,
    business_date DATE         NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    generated_at  TIMESTAMP    NOT NULL,
    completed_at  TIMESTAMP,
    row_count     INTEGER      NOT NULL DEFAULT 0,
    message       VARCHAR(500),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_report_run_definition FOREIGN KEY (report_code) REFERENCES report_definition (report_code)
);

CREATE INDEX idx_report_run_report_code ON report_run (report_code);
CREATE INDEX idx_report_run_generated_at ON report_run (generated_at);

CREATE TABLE report_row (
    row_id    VARCHAR(36)    PRIMARY KEY,
    run_id    VARCHAR(36)    NOT NULL,
    line_no   INTEGER        NOT NULL,
    dimension VARCHAR(64)    NOT NULL,
    amount    NUMERIC(16, 2) NOT NULL DEFAULT 0,
    detail    VARCHAR(200),
    CONSTRAINT fk_report_row_run FOREIGN KEY (run_id) REFERENCES report_run (run_id)
);

CREATE INDEX idx_report_row_run ON report_row (run_id);
