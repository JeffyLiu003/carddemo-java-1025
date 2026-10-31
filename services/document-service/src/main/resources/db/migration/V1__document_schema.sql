-- Document context schema; contract in docs/design/15-operations-support.md §4.1
-- status: ACTIVE / ARCHIVED
-- content_text uses TEXT: native text on PostgreSQL, parsed as CHARACTER LARGE OBJECT (CLOB) on H2

CREATE TABLE document_metadata (
    document_id   VARCHAR(36)  PRIMARY KEY,
    document_type VARCHAR(16)  NOT NULL,
    owner_type    VARCHAR(16)  NOT NULL,
    owner_id      VARCHAR(36)  NOT NULL,
    file_name     VARCHAR(200) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    content_text  TEXT         NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_document_owner ON document_metadata (owner_type, owner_id);
CREATE INDEX idx_document_created ON document_metadata (created_at);
