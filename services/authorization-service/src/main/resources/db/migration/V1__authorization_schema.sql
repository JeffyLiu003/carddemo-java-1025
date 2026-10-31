-- Authorization context schema. Maps to legacy IMS PAUTSUM0 (summary segment)/PAUTDTL1 (detail segment) and DB2 AUTHFRDS.

CREATE TABLE auth_summary (
    account_id      VARCHAR(11) PRIMARY KEY,
    credit_limit    NUMERIC(12, 2) NOT NULL,
    cash_limit      NUMERIC(12, 2) NOT NULL,
    credit_balance  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    cash_balance    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    approved_count  INT            NOT NULL DEFAULT 0,
    approved_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    declined_count  INT            NOT NULL DEFAULT 0,
    declined_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0
);

-- Authorization details (legacy AUTHFRDS). fraud_flag replaces AUTH_FRAUD.
CREATE TABLE auth_detail (
    authorization_id       VARCHAR(36) PRIMARY KEY,
    card_number            VARCHAR(16)    NOT NULL,
    auth_timestamp         TIMESTAMP      NOT NULL,
    auth_type              VARCHAR(4),
    card_expiry_date       VARCHAR(4),
    message_type           VARCHAR(6),
    message_source         VARCHAR(6),
    auth_id_code           VARCHAR(6),
    response_code          VARCHAR(2)     NOT NULL,
    reason_code            VARCHAR(4)     NOT NULL,
    processing_code        VARCHAR(6),
    transaction_amount     NUMERIC(12, 2) NOT NULL,
    approved_amount        NUMERIC(12, 2) NOT NULL,
    merchant_category_code VARCHAR(4),
    acquirer_country_code  VARCHAR(3),
    pos_entry_mode         INT,
    merchant_id            VARCHAR(15),
    merchant_name          VARCHAR(22),
    merchant_city          VARCHAR(13),
    merchant_state         VARCHAR(2),
    merchant_zip           VARCHAR(9),
    transaction_id         VARCHAR(15),
    fraud_flag             VARCHAR(1)     NOT NULL DEFAULT 'N',
    account_id             VARCHAR(11),
    customer_id            VARCHAR(9),
    created_at             TIMESTAMP      NOT NULL
);

CREATE INDEX idx_auth_detail_account ON auth_detail (account_id, auth_timestamp);
CREATE INDEX idx_auth_detail_card ON auth_detail (card_number, auth_timestamp);

-- Card-level fraud flag (BR-AUTH-06). Once flagged, every later authorization on the card is declined with 5100.
CREATE TABLE card_fraud_flag (
    card_number      VARCHAR(16) PRIMARY KEY,
    flagged_at       TIMESTAMP   NOT NULL,
    reason           VARCHAR(100),
    authorization_id VARCHAR(36)
);
