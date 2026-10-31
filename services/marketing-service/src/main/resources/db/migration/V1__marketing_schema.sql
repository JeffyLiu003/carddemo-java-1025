-- 17.3 Marketing: premium card term campaigns / enrollments / cashback grants
-- Contract: docs/design/17-premium-cashback-campaign.md §3.1

CREATE TABLE campaign (
    campaign_id                    VARCHAR(36)    PRIMARY KEY,
    code                           VARCHAR(64)    NOT NULL,
    name                           VARCHAR(128)   NOT NULL,
    description                    VARCHAR(500),
    status                         VARCHAR(16)    NOT NULL,
    start_date                     DATE           NOT NULL,
    duration_months                INTEGER        NOT NULL,
    end_date                       DATE           NOT NULL,
    eligible_product_codes         VARCHAR(255)   NOT NULL,
    min_transaction_amount         NUMERIC(14, 2) NOT NULL,
    cashback_rate                  NUMERIC(8, 6)  NOT NULL,
    max_cashback_per_transaction   NUMERIC(14, 2) NOT NULL,
    max_cashback_per_customer      NUMERIC(14, 2) NOT NULL,
    budget                         NUMERIC(14, 2) NOT NULL,
    consumed_budget                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    enrollment_required            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at                     TIMESTAMP      NOT NULL,
    updated_at                     TIMESTAMP      NOT NULL,
    version                        BIGINT         NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_campaign_code ON campaign (code);
CREATE INDEX idx_campaign_status ON campaign (status);
CREATE INDEX idx_campaign_window ON campaign (start_date, end_date);

CREATE TABLE enrollment (
    enrollment_id VARCHAR(36) PRIMARY KEY,
    campaign_id   VARCHAR(36) NOT NULL,
    account_id    VARCHAR(11) NOT NULL,
    card_number   VARCHAR(16),
    customer_id   VARCHAR(9)  NOT NULL,
    status        VARCHAR(16) NOT NULL,
    enrolled_at   TIMESTAMP   NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_enrollment_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (campaign_id)
);

CREATE UNIQUE INDEX uk_enrollment_campaign_account ON enrollment (campaign_id, account_id);
CREATE INDEX idx_enrollment_customer ON enrollment (customer_id);

CREATE TABLE reward_grant (
    reward_id          VARCHAR(36)    PRIMARY KEY,
    campaign_id        VARCHAR(36)    NOT NULL,
    transaction_id     VARCHAR(64)    NOT NULL,
    account_id         VARCHAR(11)    NOT NULL,
    card_number        VARCHAR(16),
    customer_id        VARCHAR(9)     NOT NULL,
    transaction_amount NUMERIC(14, 2) NOT NULL,
    cashback_amount    NUMERIC(14, 2) NOT NULL,
    status             VARCHAR(16)    NOT NULL,
    failure_reason     VARCHAR(500),
    created_at         TIMESTAMP      NOT NULL,
    granted_at         TIMESTAMP,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_reward_grant_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (campaign_id)
);

CREATE UNIQUE INDEX uk_reward_grant_campaign_transaction ON reward_grant (campaign_id, transaction_id);
CREATE INDEX idx_reward_grant_account ON reward_grant (account_id);
CREATE INDEX idx_reward_grant_customer ON reward_grant (customer_id);
CREATE INDEX idx_reward_grant_status ON reward_grant (status);
