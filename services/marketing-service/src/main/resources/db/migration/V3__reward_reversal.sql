-- 18.4 退款与冲正受理（docs/design/18-bonus-cashback-reversal.md，PRD REQ-CB-07）
-- 口径配置默认 NULL：业务未确认前不追扣，由 RewardReversalService 受理时返回 409。

ALTER TABLE campaign ADD COLUMN reversal_policy VARCHAR(16);

CREATE TABLE reward_reversal (
    reversal_id             VARCHAR(36)    PRIMARY KEY,
    campaign_id             VARCHAR(36)    NOT NULL,
    reward_id               VARCHAR(36)    NOT NULL,
    reversal_transaction_id VARCHAR(64)    NOT NULL,
    reversal_type           VARCHAR(16)    NOT NULL,
    reversal_amount         NUMERIC(14, 2) NOT NULL,
    clawback_amount         NUMERIC(14, 2),
    status                  VARCHAR(16)    NOT NULL,
    reason                  VARCHAR(64)    NOT NULL,
    created_at              TIMESTAMP      NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_reward_reversal_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (campaign_id),
    CONSTRAINT fk_reward_reversal_grant FOREIGN KEY (reward_id) REFERENCES reward_grant (reward_id)
);

CREATE UNIQUE INDEX uk_reward_reversal_transaction_reward
    ON reward_reversal (reversal_transaction_id, reward_id);
CREATE INDEX idx_reward_reversal_reward ON reward_reversal (reward_id);
