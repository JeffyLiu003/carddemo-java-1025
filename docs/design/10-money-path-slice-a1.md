# 10 Money Path Slice A1 Detailed Design

> Upstream: `09-scale-up-blueprint.md` Option A (account → transaction → payment → general ledger → reconciliation)
> Status: A1.1 in implementation, A1.2–A1.4 design pending review

## 1. Goals and Non-Goals

**Goals**: upgrade "adding to and subtracting from account balances" into a funds model that can support payments and authorizations:

1. Accounts are product-driven (credit limit, rates and statement cycle are configurable).
2. Funds holds and available credit are queryable, releasable and idempotently retryable.
3. Transaction posting produces double-entry journal lines, and accounts and the general ledger can be reconciled.
4. A single interbank transfer can complete "intake → screening → posting → general ledger → reconciliation → receipt".

**Non-goals (not covered by this slice)**: real settlement network integration, multi-currency accounting, a complete interest/statement engine, regulatory reports.

## 2. Increment Breakdown

| Increment | Content | Depends on | Status |
|---|---|---|---|
| A1.1 | Account productization, funds hold, available credit | None | Implemented |
| A1.2 | Transaction type catalog, posting rules, double-entry bookkeeping, reversal | A1.1 | Implemented |
| A1.3 | ledger-service: chart of accounts, journal entries, trial balance, period close | A1.2 | Implemented |
| A1.4 | Reconciliation, EOD batch processing, break handling | A1.3 | Implemented |

## 3. A1.1 Domain Model

### 3.1 Aggregates

**AccountProduct (configuration aggregate)**

| Field | Description |
|---|---|
| productCode | Product code, primary key |
| name / category / currency | Name, category (CREDIT/DEBIT/SAVINGS), currency |
| defaultCreditLimit / maxCreditLimit | Default limit, limit cap |
| defaultCashLimit / maxCashLimit | Default cash limit, cap |
| apr / monthlyFee / statementCycleDay | Annual percentage rate, monthly fee, statement cycle day |
| active | Whether the product is on sale |

**Account (account aggregate, extended)**

- Adds `productCode`; account opening must reference a valid product.
- Available credit = `creditLimit - currentBalance - Σ(ACTIVE holds)`.
- When the account status is not `Y`, holds are rejected.

**AccountHold (hold aggregate)**

| Field | Description |
|---|---|
| holdId | UUID |
| accountId / amount | Account, hold amount |
| status | ACTIVE / RELEASED / EXPIRED / CAPTURED |
| referenceType / referenceId | Source type and business idempotency key |
| createdAt / expiresAt / releasedAt | Timestamps |

### 3.2 Business Rules

| ID | Rule |
|---|---|
| AP-01 | The product referenced by the account must exist and have `active=true`. |
| AP-02 | The account `creditLimit` must not exceed the product `maxCreditLimit`. |
| AP-03 | The account `cashCreditLimit` must not exceed the product `maxCashLimit`. |
| AH-01 | Only accounts with `activeStatus=Y` can place holds. |
| AH-02 | The hold amount must be greater than 0, with at most 2 decimal places. |
| AH-03 | The hold amount must not exceed the current available credit. |
| AH-04 | Idempotent per account + `referenceId`: when an ACTIVE record already exists with the same amount, the original record is returned. |
| AH-05 | When the same `referenceId` carries a different amount, or the record is already released/expired/captured, reuse is rejected. |
| AH-06 | Releasing an already released hold returns idempotently; a non-ACTIVE state cannot be released. |
| AH-07 | An expired hold (`expiresAt < now`) no longer occupies available credit. |
| AH-08 | Creating/releasing a hold takes a pessimistic lock on the account to avoid concurrent over-holds. |

### 3.3 Database (account-service, V4)

- `account_product`: product catalog, seeded with CLASSIC / GOLD / PLATINUM.
- `account.product_code`: maps existing limits to products.
- `account_hold`: hold records, unique index on `(account_id, reference_id)`.

### 3.4 API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/account-products` | Product list |
| GET | `/api/v1/account-products/{productCode}` | Product details |
| GET | `/api/v1/accounts/{accountId}/credit-position` | Limit, balance, holds, available credit |
| GET | `/api/v1/accounts/{accountId}/holds?status=ACTIVE` | Hold list |
| POST | `/api/v1/accounts/{accountId}/holds` | Create a hold |
| DELETE | `/api/v1/accounts/{accountId}/holds/{holdId}` | Release a hold |

The account response adds `productCode`.

### 3.5 Error Codes

| Error code | HTTP | Scenario |
|---|---:|---|
| PRODUCT_NOT_FOUND | 404 | Product does not exist |
| PRODUCT_INACTIVE | 409 | Product is withdrawn from sale |
| CREDIT_LIMIT_EXCEEDS_PRODUCT_MAX | 409 | Limit exceeds the product cap |
| CASH_LIMIT_EXCEEDS_PRODUCT_MAX | 409 | Cash limit exceeds the cap |
| ACCOUNT_INACTIVE | 409 | Account status is not Y |
| INVALID_HOLD_AMOUNT | 409 | Invalid hold amount |
| INSUFFICIENT_AVAILABLE_CREDIT | 409 | Insufficient available credit |
| HOLD_NOT_FOUND | 404 | Hold record does not exist |
| HOLD_NOT_RELEASABLE | 409 | Status is not releasable |
| HOLD_REFERENCE_CONFLICT | 409 | Amount conflict on the idempotency key |
| HOLD_REFERENCE_REUSED | 409 | A closed record was reused |

### 3.6 Acceptance Criteria

1. `GET /account-products` returns the 3 seeded products; account 1 is CLASSIC and account 6 is PLATINUM.
2. Account 6 available credit = `8000 - 2450.75 - total holds`.
3. After a hold of 100, available credit drops by 100; releasing restores it; a repeated release does not error.
4. A hold above the available credit returns `INSUFFICIENT_AVAILABLE_CREDIT`.
5. Resubmitting the same `referenceId` with the same amount returns the same hold; a different amount returns `HOLD_REFERENCE_CONFLICT`.
6. Updating an account limit above the product cap returns `CREDIT_LIMIT_EXCEEDS_PRODUCT_MAX`.
7. Concurrent holds never break the available credit (pessimistic lock + integration test).

## 4. A1.2 Posting Engine (design highlights)

- `transaction_type` catalog: DEPOSIT, WITHDRAWAL, TRANSFER_IN/OUT, FEE, INTEREST, REVERSAL, ADJUSTMENT and so on; each type binds a set of `posting_rule`.
- Posting requests carry `idempotencyKey`; two tables, `posting_batch` + `posting_leg`.
- Each leg has a direction (DR/CR), a ledger account and an amount; debits and credits must balance within a batch.
- A reversal generates a reverse batch linked to the original; the original batch is marked `REVERSED`.
- Account balance updates and journal line writes happen in the same local transaction; the general ledger is synchronized asynchronously through events.

## 5. A1.3 Ledger Service (design highlights)

- Chart of accounts (COA): assets/liabilities/revenue/expenses, with a parent-child hierarchy.
- Journal entries: `journal_entry` + `journal_line`, with debit/credit balance validation.
- Posting rules: transaction type → ledger account mapping, versioned.
- Trial balance, period close, reconciliation between general ledger and sub-ledger.
- Event consumption: `PostingPosted` → generate a journal entry; the idempotency key is postingId.

## 6. A1.4 Reconciliation and EOD (design highlights)

- Reconciliation jobs: account balance vs journal line totals, general ledger vs sub-ledger, settlement files vs internal records.
- Break classification: missing, amount mismatch, duplicate, timing difference; supports adjustments and suspense posting.
- EOD job graph: interest accrual → fees → statements → past-due roll → general ledger closing → reconciliation → reports.
- Checkpoints, idempotent reruns, failure alerts.

## 7. Test Strategy

- Domain unit tests: product limits, available credit, hold state machine, concurrency boundaries.
- Controller tests: happy path, idempotency, insufficient credit, product withdrawn from sale, illegal states.
- Integration tests (later): Testcontainers + PostgreSQL, verifying migrations and locks.
- Contract tests (later): OpenAPI snapshots to prevent field breakage.
