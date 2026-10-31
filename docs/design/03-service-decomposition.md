# Microservice Decomposition and Deployment Design

## 1. Service Inventory

| Service | Port | Database | Core use cases | Key dependencies |
|---|---|---|---|---|
| web-ui | 8090 → container 8080 | — (static SPA + same-origin proxy) | Login, account view, cards, transactions, statements, reports, authorization, posting, general ledger, reconciliation/EOD, user management | All microservices |
| iam-service | 8081 | `carddemo_iam` | Login, token issuance, user CRUD | None |
| customer-service | 8082 | `carddemo_customer` | Customer query/create/update | None |
| card-service | 8083 | `carddemo_card` | Card list/detail/maintenance, lookup by account | None |
| account-service | 8084 | `carddemo_account` | Account query/maintenance, product catalog, limit and available credit, fund holds, posting, repayment, interest accrual | None |
| transaction-service | 8085 | `carddemo_transaction` | Transaction entry/query, posting, interest, bill payment, reports, transaction types, posting engine | card, account, ledger |
| ledger-service | 8088 | `carddemo_ledger` | Chart of accounts, journal entries, reversal, trial balance, period closing | None |
| reconciliation-service | 8089 | `carddemo_reconciliation` | Batch ↔ journal entry reconciliation, discrepancy handling, EOD jobs | transaction, ledger |
| payment-service | 8091 | `carddemo_payment` | Payment intake/screening/acceptance/settlement/return | account, transaction |
| billing-service | 8092 | `carddemo_billing` | Statement generation, interest/minimum payment, statement payment | account |
| limit-service | 8093 | `carddemo_limit` | Limit definitions, holds/releases, availability checks | None |
| fx-service | 8094 | `carddemo_fx` | FX rates, quotes, conversion | None |
| loan-service | 8095 | `carddemo_loan` | Loan application/approval/disbursement/repayment schedule | None |
| fraud-service | 8096 | `carddemo_fraud` | Fraud scoring/alerts | None |
| aml-service | 8097 | `carddemo_aml` | Sanctions screening/AML alerts | None |
| dispute-service | 8098 | `carddemo_dispute` | Disputes/chargebacks | None |
| collections-service | 8099 | `carddemo_collections` | Delinquency bucketing/collections | None |
| batch-service | 8100 | `carddemo_batch` | Job definitions/EOD execution | transaction, reconciliation |
| reporting-service | 8101 | `carddemo_reporting` | Transaction summaries/trial balance reports | transaction, ledger |
| notification-service | 8102 | `carddemo_notification` | Templates/notifications/sending | None |
| document-service | 8103 | `carddemo_document` | Document metadata/content | None |
| workflow-service | 8104 | `carddemo_workflow` | Workflow definitions/task approval | None |
| audit-service | 8105 | `carddemo_audit` | Append-only audit events | None |
| reference-data-service | 8106 | `carddemo_reference` | Reference data | None |
| bff-service | 8107 | — | Customer 360 aggregation | account, card, customer, transaction |
| search-service | 8108 | — | Customer/transaction search | customer, transaction |
| authorization-service | 8086 | `carddemo_authorization` | Authorization decisions, pending authorization summaries, fraud flags, expired purge | card, account, customer |

## 2. Data Ownership

| Table | Owner | Notes |
|---|---|---|
| `sec_user` | iam | User security master data |
| `customer` | customer | Customer master data (masked SSN output) |
| `card`, `card_xref`, `card_fraud_flag` (a separate table on the authorization side) | card / authorization | Card master data belongs to card; fraud flags belong to authorization |
| `account` | account | Single authority for balance and limit |
| `transaction`, `daily_transaction`, `tran_category_balance`, `tran_type`, `tran_category`, `disclosure_group` | transaction | Transaction ledger and reference data |
| `posting_type`, `posting_rule`, `posting_batch`, `posting_leg` | transaction | Posting types, rules, batches and debit/credit legs |
| `ledger_account`, `accounting_period`, `journal_entry`, `journal_line` | ledger | Chart of accounts, periods, journal entries and lines |
| `reconciliation_run`, `reconciliation_item`, `eod_run` | reconciliation | Reconciliation runs, discrepancy details and EOD job records |
| `payment_order` | payment | Payment instructions and state machine |
| `statement` | billing | Statements (unique account + period) |
| `limit_definition` | limit | Limits and usage |
| `fx_rate`, `fx_quote`, `fx_conversion` | fx | FX rates, quotes and conversion records |
| `account_credit` | account | Generic credit ledger (cashback/benefit postings, idempotent by creditId) |
| `loan_application`, `loan`, `repayment_schedule` | loan | Loan applications, loans and repayment schedules |
| `fraud_rule`, `fraud_evaluation`, `fraud_alert` | fraud | Rules, evaluations and alerts |
| `sanctions_entry`, `screening_result`, `aml_alert` | aml | Sanctions lists, screening results and alerts |
| `dispute_case` | dispute | Dispute cases |
| `delinquency_case` | collections | Delinquency cases |
| `job_definition`, `job_run` | batch | Job definitions and runs |
| `report_definition`, `report_run`, `report_row` | reporting | Report definitions/runs/results |
| `notification_template`, `notification` | notification | Templates and notifications |
| `document_metadata` | document | Document metadata and text |
| `workflow_definition`, `workflow_task` | workflow | Workflow definitions and tasks |
| `audit_event` | audit | Append-only audit events |
| `reference_item` | reference | Reference data |
| `auth_summary`, `auth_detail` | authorization | Pending authorization summaries and details |

## 3. Service Call Matrix

| Caller → Callee | Card | Account | Customer | Transaction | Ledger | Reconciliation |
|---|---|---|---|---|---|---|
| transaction-service | GET card snapshot | GET snapshot / POST posting / POST repayment / POST interest | — | — | POST journal entry | — |
| authorization-service | GET card snapshot | GET snapshot | GET customer existence | — | — | — |
| reconciliation-service | — | — | — | GET posting batches | GET journal entries/trial balance | — |
| payment-service | — | GET account | — | POST posting/reversal | — | — |
| billing-service | — | GET account/product, POST repayment | — | — | — | — |
| ledger-service | — | — | — | — | — | — |
| account-service | — | — | — | — | — | — |
| card-service | — | — | — | — | — | — |
| customer-service | — | — | — | — | — | — |
| iam-service | — | — | — | — | — | — |

Call counts (per business operation):

| Business operation | Call chain | Worst-case added latency (local Compose) |
|---|---|---|
| Transaction entry | transaction → card(1) | +10ms |
| End-of-day posting (single) | transaction → card(1) + account GET(1) + account POST(1) | +30ms |
| Bill payment | transaction → account GET(1) + account POST(1) + card(1) | +30ms |
| Interest run | transaction → account POST (once per account) | accounts × 10ms |
| Authorization decision | authorization → card(1) + account(1) + customer(1) | +30ms |
| Create posting batch | transaction → account GET(1) + account POST(1) + ledger POST(1) | +40ms (a general ledger failure only raises a warning) |
| Run reconciliation | reconciliation → transaction GET (≤40 pages) + ledger GET (≤40 pages) + trial GET(1) | Data-volume dependent; about +50ms on local Compose |
| Run EOD | reconciliation → reconciliation(1) | Same as reconciliation |
| Payment settlement | payment → account GET(1) + transaction POST(1) | +30ms |
| Payment return | payment → transaction POST reversal(1) | +20ms |
| Statement generation | billing → account GET(2) | +20ms |
| Statement payment | billing → account POST payments(1) | +10ms |

## 4. Call and Failure Semantics

| Downstream result | Upstream handling |
|---|---|
| `200` | Continue normally |
| `404` | Treated as "entity does not exist": posting → decline 101; authorization → decline 3100; transaction entry → 400 |
| `409` (business rule) | Passed through as an upstream 409, carrying the downstream `code` and `message` |
| `5xx` / timeout (3s) | Throws `RemoteServiceException` → HTTP 503 `DEPENDENCY_UNAVAILABLE`; nothing is posted and nothing is declined (authorization returns 503 and the caller retries) |

**Fail-closed principle**: in authorization scenarios, "unable to determine the limit" must never default to approval.

## 5. Consistency and Idempotency

- Bill payment: `transaction` persists first (PENDING) → calls `account /payments` → sets POSTED; a failure rolls back the local transaction (ADR-005).
- Posting: a single failure is recorded as `REJECTED` + reason code and the whole batch continues; returns `COMPLETED_WITH_REJECTS`.
- Idempotency keys: `transactionId` (posting/entry); `cardNumber + authTimestamp` maps to `authorizationId` (authorization);
  `idempotencyKey` (posting batch); `(sourceType, sourceId)` (general ledger journal entry).
- Posting batch: account call succeeds → save the batch and legs → best-effort sync to the general ledger; a general ledger failure only raises a warning and the batch's `ledgerEntryId` stays empty.
- Reversal: generates an opposite batch/opposite journal entry and marks the original record REVERSED; history is never deleted.
- Reconciliation: compares posting batches with `POSTING_BATCH` journal entries by business date; the trial balance verifies `Σdebit=Σcredit`;
  discrepancies land in `reconciliation_item`; accounting is read-only and neither balances nor entries are modified. Account balance vs general ledger awaits sub-ledger mapping.
- Payment: `payment_order.idempotency_key` is unique; the settlement posting idempotency key is `payment:<paymentId>`; returns reverse the original batch.
- Statements: `statement (account_id, period)` is unique; statement payment reuses account `/payments`, and repeated payments are idempotent.
- Limits: `(scope_type, scope_id, limit_type, currency)` is unique; holds/releases use row locks.
- FX: quotes are valid for 5 minutes and consumed once; `fx_conversion.quote_id` is unique as a backstop.

## 6. Deployment Units

```
docker compose
├── postgres (16)                # single instance, 24 databases (created by the init script, max_connections=300)
├── iam-service        :8081
├── customer-service   :8082
├── card-service       :8083
├── account-service    :8084
├── transaction-service:8085  → depends on card/account/ledger started
├── ledger-service     :8088  → depends on postgres healthy
├── reconciliation-service:8089 → depends on postgres/transaction/ledger started
├── payment-service    :8091  → depends on postgres/account/transaction started
├── billing-service    :8092  → depends on postgres/account started
├── limit-service      :8093  → depends on postgres healthy
├── fx-service         :8094  → depends on postgres healthy
├── bff-service        :8107  → depends on account/card/customer/transaction
├── search-service     :8108  → depends on customer/transaction
├── authorization-service:8086 → depends on card/account/customer healthy
└── web-ui             :8090 → host 8090 maps to container 8080 (host 8080 is taken)

# Appended by docker compose --profile extended up -d:
├── loan-service       :8095
├── fraud-service      :8096
├── aml-service        :8097
├── dispute-service    :8098
├── collections-service:8099
├── batch-service      :8100
├── reporting-service  :8101
├── notification-service:8102
├── document-service   :8103
├── workflow-service   :8104
├── audit-service      :8105
└── reference-data-service:8106
```

> `web-ui` is the **presentation layer**, not a bounded context: it only hosts the static SPA and same-origin proxies `/api/**` to
> 26 microservices by path prefix; it owns no business data and implements no business rules. The browser therefore needs only one origin and no CORS.

Startup order: `postgres` (healthcheck: `pg_isready`) → services without dependencies → `ledger` → `transaction` →
`reconciliation` / `authorization` / `web-ui`.
Every service exposes Spring Boot Actuator `/actuator/health` for liveness checks; downstream clients are lazily loaded, so
service startup does not depend on downstream availability and a failed call returns `503`. In `docker-compose.yml`,
`transaction`/`authorization` use `condition: service_started` for their downstreams (which only guarantees the container has started,
avoiding deadlocks caused by startup order).

## 7. Capacity and Scaling

| Service | Scaling characteristics | Notes |
|---|---|---|
| authorization | Stateless, read-heavy and write-light, can scale horizontally; summary rows have per-account hot spots | Concurrent authorizations for the same account must serialize (optimistic locking retry) |
| transaction | Write-heavy; batch processing must run on a single instance (to avoid duplicate posting) | The posting endpoint is idempotent by `transactionId`, so multiple instances are fine |
| account | Per-account hot spots; row-level updates | Per-account QPS is limited by row locks/version conflicts |
| card / customer | Read-heavy and write-light | Caching can be added (not done in this phase) |
| iam | Low traffic | — |

## 8. Configuration

| Variable | Service | Default |
|---|---|---|
| `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` | All | `localhost`/`5432`/`carddemo_*`/`carddemo`/`carddemo` |
| `CARD_SERVICE_URL` | transaction, authorization | `http://localhost:8083` (`http://card-service:8083` inside Compose) |
| `ACCOUNT_SERVICE_URL` | transaction, authorization | `http://localhost:8084` |
| `CUSTOMER_SERVICE_URL` | authorization | `http://localhost:8082` |
| `IAM_TOKEN_SECRET` | iam | Demo secret (in production it must be injected from a secret manager) |
| `IAM_TOKEN_TTL_SECONDS` | iam | `3600` |
