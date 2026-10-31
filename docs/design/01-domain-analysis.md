# Domain Analysis: Subdomains, Aggregates and Invariants

> Basis: `docs/legacy/02-five-level-model.md` (L1–L5) and `03-business-rule-catalog.md`.
> This document answers "what is core in the business, where the boundaries lie, and how aggregates are partitioned"; it does not address technical implementation.

## 1. Subdomain Partitioning (Core / Supporting / Generic)

| Subdomain | Type | Rationale | Corresponding capability |
|---|---|---|---|
| Authorization | Core Domain | Real-time decision of "approve/decline" that directly determines credit risk and financial loss; the rules are complex and must be auditable | C7 |
| Transaction / Posting | Core Domain | The transaction master record is an immutable source of truth; balances and category accumulations are derived state; posting and interest directly affect accounting correctness | C6, C8, C9 |
| Account | Core Domain | Limit, balance and billing cycle are the basis of risk and billing; most sensitive to concurrent updates | C4 |
| Ledger | Core Domain (new) | Double-entry bookkeeping is the accounting projection of financial facts; debit/credit balance, period closing and trial balance are the audit foundation | New |
| Reconciliation | Supporting Domain (new) | Verifies integrity and amount consistency between postings and the general ledger; accounting is read-only and discrepancies are traceable; entry point for EOD jobs | New |
| Payment | Core Domain (new) | The payment instruction lifecycle determines outbound funds; screening, settlement and returns are closely related to posting | New |
| Billing | Supporting Domain (new) | Statements, interest and minimum payment are the basis of billing and repayment; depends on account snapshots | New |
| Limit | Supporting Domain (new) | Limit holds and releases provide risk-control input for authorization/payment; the rules are relatively independent | New |
| FX | Supporting Domain (new) | Multi-currency quotes and conversion; can evolve independently | New |
| Card | Core Domain (strongly supporting in nature) | The card number–account–customer mapping and card status determine whether a transaction can occur; however the domain rules are relatively thin | C5 |
| Customer | Supporting Domain | Master data maintenance; the rules are mostly validation and carry no financial semantics | C3 |
| Identity and Access (IAM) | Generic Domain | Login and user maintenance, needed by any system; can be bought/replaced | C1, C2 |
| Reporting | Supporting Domain | Read-only derived views with no write semantics; can evolve independently into a data platform | C11 |
| Data Migration | Generic / one-off | Batch import and export; an implementation tool rather than a business capability | C12, C13 |

**Conclusion**: Core domains = authorization, transaction/accounting, ledger, payment, account, card; supporting domains = customer, reconciliation, billing, limit, FX, reporting; generic domains = IAM and the migration tool.

## 2. Aggregate Design

### 2.1 Aggregate Inventory

| Aggregate root | Internal entities / value objects | Invariants that must hold within the transaction boundary |
|---|---|---|
| `Account` | Limit, balance, billing cycle, account group | Status ∈ {Y,N}; all five amounts non-null; all three dates non-null with century 19/20; on posting, the balance and the current-cycle credit/debit change together; the balance is not negative after repayment |
| `AccountProduct` | Limit bounds, APR, monthly fee, statement day | Product code is unique; maximum limit ≥ default value; a discontinued product cannot be referenced by new accounts |
| `AccountHold` | Amount, status, source, expiry time | Amount > 0; only ACTIVE holds consume limit; the same account + referenceId is idempotent; state machine ACTIVE→RELEASED/EXPIRED/CAPTURED |
| `Card` | Card number, account number, customer number, expiry, status | Status ∈ {Y,N}; month 1–12, year 1950–2099; embossed name is non-empty and letters/spaces only; the CVV cannot be overwritten by online maintenance |
| `Customer` | Name, address, SSN, FICO, phone, EFT | First/last name, city and address line 1 are required; the first SSN segment is valid; FICO 300–850; DOB is not later than today |
| `TransactionRecord` | Type, category, amount, merchant, timestamp | Type/category/source/description/amount are required; the amount and date formats are valid; the transaction number is unique and 16 digits |
| `PostingBatch` | Type, account, amount, idempotency key, debit/credit legs | Amount > 0; Σdebit = Σcredit = amount; the idempotency key is unique; a reversal produces an opposite batch and the original batch is set to REVERSED |
| `JournalEntry` | Source, posting time, status, lines | At least two lines; line amount > 0; Σdebit = Σcredit; the account exists and is active; the period is not closed; `(sourceType, sourceId)` is idempotent |
| `AccountingPeriod` | Period, status, closing time | Period format is YYYY-MM; OPEN → CLOSED is one-way; a closed period cannot accept new entries |
| `ReconciliationRun` | Business date, statistics, status | Statistics within a run are self-consistent; a downstream failure sets FAILED and preserves the error message; accounting is read-only |
| `ReconciliationItem` | Check type, reference, amount, difference, status | difference = expected − actual; resolving a discrepancy only updates resolved and the note |
| `PaymentOrder` | Debit account, creditor, amount, idempotency key, status | Amount > 0; status is one-way; the idempotency key is unique; settlement key `payment:<id>` |
| `Statement` | Account, period, balance, purchases/repayments, interest, minimum payment | Account + period is unique; closing balance = balance; interest = balance × APR/12; minimum payment = min(balance, max(25, balance × 3%)) |
| `LimitDefinition` | Scope, type, limit, used | Limit > 0; used ≤ limit; unique business key; a release must not make used < 0 |
| `FxQuote` | Currency pair, amount, FX rate, validity | Amount > 0; the currencies differ; valid for 5 minutes; can be consumed only once |
| `TranCategoryBalance` | (account, type, category) → accumulated amount | Written in the same transaction as the transaction; initialized with the amount when absent |
| `AuthorizationCase` | Request snapshot, decision, reason code | Reason-code priority for the decision is fixed; approved amount ∈ {0, transaction amount}; a decline must carry a reason code |
| `PendingAuthorizationSummary` | Limit, credit balance, approved/declined counts and amounts | Approval: count + 1, amount +, credit balance +; decline: count + 1, amount +; credit balance = Σ non-expired approved amounts (rolled back on purge) |
| `SecUser` | User ID, name, password hash, type | User ID is unique and 8 characters; type ∈ {A,U}; at least one A is retained |

### 2.2 Basis for Aggregate Boundaries

1. **Account and Customer are separate**: a customer can hold multiple accounts (the 1:N relationship in `CVACT03Y`) and their lifecycles differ;
   the legacy `COACTUPC` same-screen update is UI coupling (see ADR-004).
2. **Card and Account are separate**: a card is a payment instrument and an account is the credit vehicle; one account can have multiple
   cards (`CARD-ACCT-ID` in `CVACT02Y`), and card status and account status change independently.
3. **TransactionRecord and Account are separate**: transactions form an immutable ledger and the balance can be recomputed; merging them would mean "changing the balance changes history".
4. **TranCategoryBalance is an independent aggregate**: accumulated by `(account, type, category)` and used for interest calculation; its update
   frequency matches transactions but it can be rebuilt independently.
5. **AuthorizationCase and Account are separate**: an authorization is a "request that holds limit", and its summary (PendingAuthSummary)
   is local state in the authorization context; the account balance is a posted fact. The two cannot be merged, otherwise real-time authorization and batch processing would lock each other.
6. **SecUser is independent**: the security context and the business contexts share no lifecycle.

## 3. Domain Services and Domain Events

| Domain service | Responsibility | Owning context |
|---|---|---|
| `PostingPolicy` | Validates card number/account/limit/expiry and produces decline reason codes 100–103 | Transaction |
| `InterestCalculator` | `accumulated amount × annual rate ÷ 1200` (HALF_UP, 2 decimals) | Transaction |
| `AuthorizationPolicy` | Decides approve/decline and reason codes 3100–9000 by priority | Authorization |
| `AvailableCreditCalculator` | Uses `limit − credit balance` when a summary exists, otherwise `limit − current balance` | Authorization |

Domain events (expressed in this phase as synchronous calls plus status fields; they may be published externally in future):

| Event | Trigger | Subscribers (planned) |
|---|---|---|
| `TransactionPosted` | Posting succeeded | Account (balance), reporting |
| `BillPaymentPosted` | Bill payment succeeded | Account (debit), notification |
| `AuthorizationDecided` | Authorization decision completed | Risk control, reporting |
| `CardFraudFlagged` | Manual fraud flag | Authorization (subsequent decline 5100) |
| `InterestAccrued` | Interest settlement | Account (balance), billing |

## 4. Semantic Differences from the Legacy System (Must Be Explicitly Acknowledged)

| Difference | Legacy behavior | New behavior | Reason |
|---|---|---|---|
| Account + customer saved on one screen | One CICS transaction writes two files | Two independent PUTs, no cross-aggregate transaction | Aggregate boundary (ADR-004) |
| Submit with no changes | Error `No change detected...` | PUT succeeds idempotently | HTTP semantics (REST idempotency) |
| Record locking | `READ UPDATE` long transaction lock | `@Version` optimistic locking | No session lock on the web (ADR-006) |
| Card maintenance overwrites the CVV | The CVV becomes empty/zero on rewrite | Left unchanged | Defect fix (NR-CARD-01) |
| Authorization reason codes 4200/4300/5100/5200 | Dead code, never triggered | Implemented per business intent | Complete the semantics (BR-AUTH-04..06) |
| Authorization available credit | After a summary is created the current amount owed is ignored, inflating the limit | Always deducts the amount owed + non-expired holds | Credit risk defect (NR-AUTH-03) |
| Expired authorization purge | Does not roll back the credit balance | Rolls back and recomputes the summary | Limit leakage defect (NR-AUTH-01) |
| Password | Plaintext, case-sensitivity failures | PBKDF2, uniform errors | Security baseline (ADR-008) |
| Report output | 133-byte print file | JSON + totals semantics | Layout belongs to the reporting platform (BR-RPT-05) |

## 5. Invariant Verification Checklist (Before Coding)

- [x] Every aggregate root has a unique identity and a clear transaction boundary
- [x] Cross-aggregate write operations are identified (account + customer, transaction + account, authorization + account) and have an orchestration plan
- [x] Every core business rule traces back to legacy evidence (see the rule catalog)
- [x] Semantic differences are recorded with reasons and impact
- [x] Unresolved items (U1–U6) have owners and interim handling
