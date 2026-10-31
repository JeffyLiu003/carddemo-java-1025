# 13 Payments and Billing Slice P2

> Upstream: `09-scale-up-blueprint.md` P2
> Status: implemented (P2)

## 1. Scope

| Service | Port | Database | Core capabilities |
|---|---|---|---|
| payment-service | 8091 | `carddemo_payment` | Payment instruction intake, screening, settlement, return |
| billing-service | 8092 | `carddemo_billing` | Statement generation, minimum payment, interest, statement payment |
| limit-service | 8093 | `carddemo_limit` | Customer/account/channel limits, usage and validation |
| fx-service | 8094 | `carddemo_fx` | FX rate maintenance, quotes, conversion records |

Payment settlement is booked through a transaction-service posting batch (`PAYMENT` type, DECREASE);
statement payment reuses account-service `/payments`; limits and FX are standalone in this phase, with integration reserved for later.

## 2. payment-service

### 2.1 Table `payment_order`

`payment_id`, `idempotency_key` (unique), `debtor_account_id`, `creditor_name`,
`creditor_account_number`, `creditor_bank_code`, `amount`, `currency`, `payment_type`
(ACH/SEPA/WIRE/INSTANT), `status` (RECEIVED/SCREENED/ACCEPTED/SETTLED/RETURNED/REJECTED),
`reference`, `description`, `transaction_batch_id`, `reject_reason`, `created_at`, `updated_at`,
`settled_at`, `returned_at`, `version`.

### 2.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/payment-orders` | Accept a payment (201, idempotent on `idempotencyKey`) |
| GET | `/api/v1/payment-orders/{paymentId}` | Details |
| GET | `/api/v1/payment-orders?page=&size=&status=&accountId=` | Paged query |
| POST | `/api/v1/payment-orders/{paymentId}/accept` | Screening passed → ACCEPTED |
| POST | `/api/v1/payment-orders/{paymentId}/settle` | Call transaction posting → SETTLED |
| POST | `/api/v1/payment-orders/{paymentId}/return` | Return; reverses if already settled → RETURNED |

Create request:

```json
{"idempotencyKey":"pay-20261026-0001","debtorAccountId":"00000000006",
 "creditorName":"Acme Utility","creditorAccountNumber":"1234567890123456",
 "creditorBankCode":"BOFAUS3N","amount":120.00,"currency":"USD",
 "paymentType":"ACH","reference":"INV-2026-10","description":"Utility bill"}
```

Rules:

- Amount > 0 and a 3-character currency; the account must exist and be `Y`; `paymentType` must be valid.
- Screening runs automatically on creation: `creditorName` non-empty and not on the deny list → `SCREENED`; otherwise `REJECTED`.
- `accept` is only allowed from SCREENED; repeated calls are idempotent.
- `settle` is only allowed from ACCEPTED; it calls
  `POST /api/v1/posting-batches` with `payment:<paymentId>` as the idempotency key (type `PAYMENT`, accountEffect DECREASE) and saves `transactionBatchId` on success.
- `return` is allowed from ACCEPTED/SETTLED; when already settled it calls `POST /api/v1/posting-batches/{batchId}/reversal`.
- Downstream unavailable → 503 `DEPENDENCY_UNAVAILABLE` and the payment order status is unchanged.

## 3. billing-service

### 3.1 Table `statement`

`statement_id`, `account_id`, `period` (YYYY-MM), `cycle_date`, `opening_balance`, `closing_balance`,
`purchases`, `payments`, `fees`, `interest`, `minimum_payment`, `due_date`, `status`
(GENERATED/PAID/OVERDUE), `generated_at`, `paid_at`, `version`, unique `(account_id, period)`.

### 3.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/statements/generate` | Generate a statement (201; idempotent per account + period, returns the existing one) |
| GET | `/api/v1/statements/{statementId}` | Details |
| GET | `/api/v1/statements?accountId=&page=&size=` | Paged query |
| POST | `/api/v1/statements/{statementId}/pay` | Pay the statement in full (calls account `/payments`) |

Generation request:

```json
{"accountId":"00000000001","period":"2026-10"}
```

Calculation basis:

- Reads the account and product from account-service: `credit-position` + `account-products/{code}`.
- `closing_balance = currentBalance`; `purchases = currentCycleCredit`;
  `payments = abs(min(currentCycleDebit, 0))`;
  `opening_balance = closing_balance - purchases + payments`.
- `interest = closing_balance × APR ÷ 12` (HALF_UP, 2 decimals); `fees = 0` (the fee engine is connected later).
- `minimum_payment = closing_balance == 0 ? 0 : max(25.00, closing_balance × 3%)`, capped at the closing balance.
- `cycle_date = last day of the period`; `due_date = cycle_date + 25 days`.
- `pay`: when the closing balance is 0, mark PAID directly; otherwise call `POST /accounts/{id}/payments` and mark PAID on success.

## 4. limit-service

### 4.1 Table `limit_definition`

`limit_id`, `scope_type` (CUSTOMER/ACCOUNT/CHANNEL), `scope_id`, `limit_type`
(PURCHASE/CASH/DAILY/TRANSFER), `currency`, `amount`, `used_amount`, `active`, `version`;
unique `(scope_type, scope_id, limit_type, currency)`.

### 4.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/limits` | Create a limit (201) |
| GET | `/api/v1/limits?scopeType=&scopeId=` | List |
| GET | `/api/v1/limits/{limitId}` | Details |
| PUT | `/api/v1/limits/{limitId}` | Adjust the amount (must not fall below the used amount) |
| POST | `/api/v1/limits/{limitId}/usage` | Consume limit; over the limit 409 `LIMIT_EXCEEDED` |
| POST | `/api/v1/limits/{limitId}/release` | Release limit |
| GET | `/api/v1/limits/check?scopeType=&scopeId=&limitType=&currency=&amount=` | Availability check |

Rules: the limit amount > 0; used ≤ amount; when no matching limit exists, `allowed=false`; `release` must not drive used below 0.

## 5. fx-service

### 5.1 Tables

- `fx_rate`: `pair_id`, `base_currency`, `quote_currency`, `rate`, `effective_at`, `active`, `version`,
  unique `(base_currency, quote_currency)`.
- `fx_quote`: `quote_id`, `from_currency`, `to_currency`, `source_amount`, `rate`, `converted_amount`,
  `created_at`, `expires_at`, `consumed`.
- `fx_conversion`: `conversion_id`, `quote_id`, `from_currency`, `to_currency`, `source_amount`,
  `rate`, `converted_amount`, `created_at`.

### 5.2 API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/fx/rates` | FX rate list |
| POST | `/api/v1/fx/rates` | Maintain FX rates (upsert) |
| POST | `/api/v1/fx/quotes` | Quote (valid for 5 minutes) |
| POST | `/api/v1/fx/quotes/{quoteId}/execute` | Execute a conversion (a quote can be consumed only once) |
| GET | `/api/v1/fx/conversions` | Conversion records |

Seeds: USD/CNY 7.10, USD/EUR 0.92, EUR/CNY 7.72. Amounts use 2 decimals, FX rates 6 decimals.

## 6. Frontend

- `#/payments` (business): create payments, filter by status, accept/settle/return.
- `#/billing` (business): generate statements, query by account, statement details, pay in full.
- `#/limits` (admin): create/adjust limits, consume/release, availability check.
- `#/fx` (business): FX rate list, quote, execute conversion, conversion records.
- The proxy gains four groups of paths; navigation and home page entries are updated in sync.

## 7. Acceptance

1. Payment: create → automatic screening → accept → settle (balance decreases, general ledger entries generated) → return (reversal, balance restored).
2. Payment idempotency: the same `idempotencyKey` returns the same payment order; repeated settle/return do not post twice.
3. Billing: only one statement per account + period; amount/interest/minimum payment/due date are calculated correctly; the status is PAID after payment.
4. Limits: usage never exceeds the limit; over the limit 409; availability is restored after release; check returns false when no matching limit exists.
5. FX: a quote is valid for 5 minutes; after execution the quote cannot be consumed again; the converted amount is calculated correctly.
6. The four frontend flows are operable; Docker Compose starts (currently 16 containers by default, 28 with `--profile extended`); all tests in the repository are green.
