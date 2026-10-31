# Bounded Context Definitions

## 1. Context Inventory

> Note: `web-ui` (business UI + same-origin proxy) is the **presentation layer**; it does not belong to any bounded context and owns neither data nor business rules.

| Context | Type | Service | Database | Owned aggregates |
|---|---|---|---|---|
| Identity and Access (IAM) | Generic Domain | iam-service :8081 | `carddemo_iam` | SecUser |
| Customer | Supporting Domain | customer-service :8082 | `carddemo_customer` | Customer |
| Card | Core Domain | card-service :8083 | `carddemo_card` | Card, CardXref |
| Account | Core Domain | account-service :8084 | `carddemo_account` | Account, AccountProduct, AccountHold |
| Transaction and Accounting | Core Domain | transaction-service :8085 | `carddemo_transaction` | TransactionRecord, TranCategoryBalance, TranType, TranCategory, DisclosureGroup, PostingBatch |
| Ledger | Core Domain (new) | ledger-service :8088 | `carddemo_ledger` | LedgerAccount, JournalEntry, AccountingPeriod |
| Reconciliation | Supporting Domain (new) | reconciliation-service :8089 | `carddemo_reconciliation` | ReconciliationRun, ReconciliationItem, EodRun |
| Payment | Core Domain (new) | payment-service :8091 | `carddemo_payment` | PaymentOrder |
| Billing | Supporting Domain (new) | billing-service :8092 | `carddemo_billing` | Statement |
| Limit | Supporting Domain (new) | limit-service :8093 | `carddemo_limit` | LimitDefinition |
| FX | Supporting Domain (new) | fx-service :8094 | `carddemo_fx` | FxRate, FxQuote, FxConversion |
| Loan | Core Domain (new) | loan-service :8095 | `carddemo_loan` | LoanApplication, Loan, RepaymentSchedule |
| Fraud | Supporting Domain (new) | fraud-service :8096 | `carddemo_fraud` | FraudRule, FraudEvaluation, FraudAlert |
| AML | Supporting Domain (new) | aml-service :8097 | `carddemo_aml` | SanctionsEntry, ScreeningResult, AmlAlert |
| Dispute | Supporting Domain (new) | dispute-service :8098 | `carddemo_dispute` | DisputeCase |
| Collections | Supporting Domain (new) | collections-service :8099 | `carddemo_collections` | DelinquencyCase |
| Batch | Supporting Domain (new) | batch-service :8100 | `carddemo_batch` | JobDefinition, JobRun |
| Reporting | Supporting Domain (new) | reporting-service :8101 | `carddemo_reporting` | ReportDefinition, ReportRun, ReportRow |
| Notification | Supporting Domain (new) | notification-service :8102 | `carddemo_notification` | NotificationTemplate, Notification |
| Document | Supporting Domain (new) | document-service :8103 | `carddemo_document` | DocumentMetadata |
| Workflow | Supporting Domain (new) | workflow-service :8104 | `carddemo_workflow` | WorkflowDefinition, WorkflowTask |
| Audit | Generic Domain (new) | audit-service :8105 | `carddemo_audit` | AuditEvent |
| Reference Data | Generic Domain (new) | reference-data-service :8106 | `carddemo_reference` | ReferenceItem |
| BFF | Presentation layer (new) | bff-service :8107 | — (stateless) | CustomerOverview |
| Search | Supporting Domain (new) | search-service :8108 | — (stateless) | SearchResult |
| Authorization | Core Domain | authorization-service :8086 | `carddemo_authorization` | AuthorizationCase, PendingAuthorizationSummary, CardFraudFlag |

## 2. Responsibilities and Boundaries of Each Context

### 2.1 IAM (iam-service)

- **Responsibility**: login authentication, user CRUD, user type (A/U) and menu routing, token issuance.
- **Aggregates**: `SecUser` (user ID, name, password hash, type, version).
- **Invariants**: user ID is unique and 8 characters; type ∈ {A,U}; at least one administrator is retained; a user cannot delete themselves (optional `X-User-Id`).
- **External contract**: `/api/v1/auth/*`, `/api/v1/users/*`.
- **Explicitly out of scope for this context**: business permissions (menu-item level), session management (stateless HTTP), single sign-on.
- **Legacy mapping**: COSGN00C, COADM01C, COMEN01C (menu-item data only), COUSR00C–03C.
- **Security decisions**: ADR-007 (token validation not enforced), ADR-008 (PBKDF2).

### 2.2 Customer (customer-service)

- **Responsibility**: customer master data (name, address, contact details, SSN, DOB, FICO, EFT, primary card holder flag).
- **Invariants**: see BR-CUST-02..12.
- **External contract**: `GET/POST/PUT /api/v1/customers`.
- **Out of scope**: account limits, card status, and the customer–account ownership relationship (maintained by the Xref in card-service).
- **Legacy mapping**: CUSTDAT, CBCUS01C, the customer portion of COACTUPC, the C records of CBEXPORT/CBIMPORT.
- **Data minimization**: responses return only the masked SSN; the full SSN is never returned.

### 2.3 Card (card-service)

- **Responsibility**: card master data, card number ↔ account ↔ customer mapping (Xref), card list/detail/maintenance, lookup by account.
- **Invariants**: see BR-CARD-01..11, NR-CARD-01/02.
- **External contract**: `/api/v1/cards`, `/api/v1/cards/by-account/{accountId}`.
- **Out of scope**: limits and balances (Account), authorization decisions (Authorization), customer profiles (Customer).
- **Legacy mapping**: CARDDAT, CCXREF, CXACAIX, COCRDLIC/SLC/UPC, CBACT02C, CBACT03C.

### 2.4 Account (account-service)

- **Responsibility**: account master data, balances and limits, posting, repayment, interest accrual.
- **Invariants**: see BR-ACCT-02..06, NR-ACCT-01.
- **External contract**: `/api/v1/accounts`.
- **Out of scope**: transaction master records (Transaction), authorization holds (Authorization), customer profiles (Customer).
- **Legacy mapping**: ACCTDAT, COACTVWC, COACTUPC (account portion), the account update segment of CBTRN02C, the account settlement of CBACT04C, the debit segment of COBIL00C.

### 2.5 Transaction (transaction-service)

- **Responsibility**: transaction master records, transaction type/category reference data, category accumulation, end-of-day posting, interest accrual, bill payment orchestration, transaction reports.
- **Invariants**: see BR-TRN-*, BR-POST-*, BR-INT-*, BR-BILL-*.
- **External contract**: `/api/v1/transactions`, `/api/v1/postings`, `/api/v1/interest-runs`,
  `/api/v1/bill-payments`, `/api/v1/reports/transactions`, `/api/v1/transaction-types`.
- **Out of scope**: ultimate authority for account balances (Account), card status (Card), authorization decisions (Authorization).
- **Legacy mapping**: TRANSACT, DALYTRAN, TCATBALF, TRANTYPE, TRANCATG, DISCGRP, COTRN00C/01C/02C, CBTRN01C/02C/03C, CBACT04C, COBIL00C, CORPT00C.

### 2.6 Authorization (authorization-service)

- **Responsibility**: authorization decisions, pending authorization summaries and details, card-level fraud flags, expired authorization purge.
- **Invariants**: see BR-AUTH-01..13, NR-AUTH-01/02.
- **External contract**: `/api/v1/authorizations`, `/api/v1/pending-authorizations/{accountId}`,
  `/api/v1/authorization-purges`.
- **Out of scope**: account balances and limits (it only reads snapshots), transaction posting (an authorization approval is not a posting), customer profiles.
- **Legacy mapping**: COPAUA0C, COPAUS0C/1C/2C, CBPAUP0C, IMS PAUTSUM0/PAUTDTL1, DB2 AUTHFRDS.

### 2.7 Ledger (ledger-service, new)

- **Responsibility**: chart of accounts, double-entry journal, reversal, trial balance, accounting period closing.
- **Aggregates**: `LedgerAccount` (account), `JournalEntry` (journal entry, including `JournalLine`), `AccountingPeriod` (period).
- **Invariants**: at least two lines, amount > 0, Σdebit = Σcredit; the account exists and is active; the period is not closed; `(sourceType, sourceId)` is idempotent.
- **External contract**: `/api/v1/ledger-accounts`, `/api/v1/journal-entries`, `/api/v1/trial-balance`,
  `/api/v1/accounting-periods`.
- **Out of scope**: account balances (Account is authoritative), business transaction semantics (Transaction is authoritative), customer/card status.
- **Integration**: after Transaction posts successfully it records the journal entry synchronously (a failure only raises a warning); reversals generate opposite records on each side independently.

### 2.8 Reconciliation (reconciliation-service, new)

- **Responsibility**: reconcile posting batches against `POSTING_BATCH` journal entries by business date, verify the trial balance, record discrepancies and resolution notes, and provide the entry point for EOD jobs.
- **Aggregates**: `ReconciliationRun` (run), `ReconciliationItem` (discrepancy detail), `EodRun` (job run).
- **Invariants**: business and general ledger data are read-only and accounting is not modified; resolving a discrepancy only updates the reconciliation record; a downstream failure keeps FAILED and the error message.
- **External contract**: `/api/v1/reconciliation-runs`, `/api/v1/reconciliation-items`, `/api/v1/eod-runs`.
- **Out of scope**: account balances, posting rules and journal entry writes (owned by Account/Transaction/Ledger respectively).
- **Integration**: reads transaction batches, ledger journal entries and the trial balance; never modifies either in return.

### 2.9 Payment (payment-service, new)

- **Responsibility**: payment instruction intake, creditor screening, acceptance, settlement (calling transaction posting), return (reversal).
- **Aggregates**: `PaymentOrder`, states RECEIVED→SCREENED→ACCEPTED→SETTLED, with RETURNED/REJECTED as terminal states.
- **Invariants**: amount > 0; currency/type are valid; state transitions are one-way; `idempotencyKey` is unique; the settlement idempotency key is `payment:<id>`.
- **Integration**: Account validates the account; Transaction creates the posting batch and reversal; balances are never modified directly.

### 2.10 Billing (billing-service, new)

- **Responsibility**: generate statements by account + period, computing opening/closing balances, purchases/repayments, interest, minimum payment and due date; supports full payment.
- **Aggregates**: `Statement`, with `(accountId, period)` unique; states GENERATED/PAID/OVERDUE.
- **Integration**: Account reads the account/product/cycle accumulations and executes repayment.

### 2.11 Limit (limit-service, new)

- **Responsibility**: product limit definitions for customer/account/channel, holds/releases and availability checks.
- **Aggregates**: `LimitDefinition`; invariants amount > 0, used ≤ amount, unique `(scopeType, scopeId, limitType, currency)`.
- **Boundary**: standalone in this phase; integration into the authorization chain is future work.

### 2.12 FX (fx-service, new)

- **Responsibility**: direct currency pair FX rate maintenance, 5-minute quotes, single-use conversion.
- **Aggregates**: `FxRate`, `FxQuote`, `FxConversion`; expired quotes or repeated consumption are rejected.
- **Boundary**: direct currency pairs only; no triangulation or exposure management.

### 2.13 P3/P4/P5 Extension Contexts (new)

| Context | Core responsibility | Key invariants |
|---|---|---|
| Loan | Application → approval → disbursement → repayment | One-way status; equal-installment schedule; repayments offset installments |
| Fraud | Rule scoring → decision → alert | Score thresholds; REVIEW/REJECT generate alerts |
| AML | List screening → alert | Normalized matching; alerts only on a hit |
| Dispute | Open case → review → provisional credit → chargeback → close | One-way status; provisional credit ≤ disputed amount |
| Collections | Delinquency bucketing → PTP → close | Bucket boundaries; PTP date in the future; closed cases are immutable |
| Batch | Job definition → run | RUNNING→COMPLETED/FAILED; a downstream failure retains the record |
| Reporting | Report definition → run → result rows | Row count matches downstream; failures keep FAILED |
| Notification | Template → notification → send | Template is active; repeated sends are idempotent |
| Document | Metadata + text → archive/delete | UTF-8 size; archiving is one-way |
| Workflow | Definition → task → approve/reject | Only PENDING tasks can be decided; a repeated decision returns 409 |
| Audit | Append event → search | Append-only, no updates or deletes |
| Reference | Reference item CRUD | `(domain, code)` is unique; case normalization |
| BFF | Customer 360 aggregation | An account failure yields 404/503; other downstreams degrade |
| Search | Customer/transaction search | Type whitelist; downstream failures degrade |

## 3. Cross-Context Collaboration (Who Asks Whom)

| Scenario | Initiator | Dependency | Data flow |
|---|---|---|---|
| Transaction entry | Transaction | Card | Card number → account/customer |
| Posting | Transaction | Card, Account | Validate card and account; write account balance |
| Bill payment | Transaction | Account, Card | Read balance; write transaction; debit |
| Interest | Transaction | Account | Write interest transaction; settle balance and billing cycle |
| Authorization decision | Authorization | Card, Account, Customer | Read card/account/customer snapshots; write local authorization summary |
| Posting batch | Transaction | Account, Ledger | Change account balance; persist batch and legs; sync general ledger |
| Ledger query/close | Web UI → Ledger | — | Read-only local accounts, entries and periods |
| Reconciliation | Reconciliation | Transaction, Ledger | Read batches/journal entries/trial balance; write local reconciliation records |
| EOD | Web UI → Reconciliation | Transaction, Ledger | Trigger reconciliation and record the job result |
| Payment settlement | Payment | Account, Transaction | Validate account; create posting batch/reversal |
| Statement generation/payment | Billing → Account | Account | Read account/product/cycle accumulations; execute repayment |
| Limit check | Caller → Limit | — | Hold/release/availability check |
| Conversion | Caller → FX | — | Quote and conversion records |
| Loan | Caller → Loan | — | Application/approval/disbursement/repayment |
| Risk handling | Caller → Fraud/AML/Dispute/Collections | — | Scoring/screening/case state machine |
| Operational jobs | Caller → Batch/Reporting/Notification/Document/Workflow/Audit/Reference | Downstream on demand | Jobs, reports, notifications, documents, approvals, audit, reference data |
| Customer 360 | Web UI → BFF | Account/Card/Customer/Transaction | Aggregation and degradation |
| Unified search | Web UI → Search | Customer/Transaction | Real-time filtering |
| Transaction report | Transaction | — | Read-only local transactions and reference data |
| Account view | Caller (UI/BFF) | Account, Customer, Card | Combination of three queries |

## 4. Which Legacy Responsibilities Were Merged or Moved

| Legacy responsibility | New owner | Notes |
|---|---|---|
| `CXACAIX` account→customer/card lookup | Card | Xref is mapping data of the card context |
| Transaction list filtered by card number | Transaction (has a local `card_number`) | No call to Card needed |
| Report grouping by card number | Transaction | The transaction master record already contains the card number |
| Transaction type maintenance | Transaction | Reference data belongs to transaction semantics |
| Branch migration import/export | Standalone migration tool (not implemented in this phase) | A one-off implementation tool, not a business capability |
| MQ account lookup | Account HTTP API + adapter (deferred) | Contract equivalent |

## 5. How Context Boundaries Are Accepted

1. Database layer: a service account can access only its own database (each service has its own connection string in Compose).
2. Code layer: the `domain` package must contain no cross-context entity references beyond `RestClient`/`JdbcTemplate`.
3. Contract layer: cross-context calls use only the DTOs in `docs/design/05-api-catalog.md`; JPA entities are never shared.
4. Test layer: each service's end-to-end tests depend only on its own database and mocked downstreams.
