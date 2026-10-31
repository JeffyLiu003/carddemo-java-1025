-- Reference data code tables: (domain, code) is unique
CREATE TABLE reference_item (
    item_id    VARCHAR(36) PRIMARY KEY,
    domain     VARCHAR(32) NOT NULL,
    code       VARCHAR(32) NOT NULL,
    label      VARCHAR(120) NOT NULL,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_reference_item_domain_code UNIQUE (domain, code)
);

CREATE INDEX idx_reference_item_domain ON reference_item (domain, sort_order);
