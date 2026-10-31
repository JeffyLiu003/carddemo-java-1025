# API Catalog (Service-to-Service Contracts)

> All services follow `docs/design/07-java-conventions.md`. Base path `/api/v1`, JSON, UTF-8.
> Business-facing UI: `http://localhost:8090/` (web-ui hosts the SPA and same-origin proxies all `/api/**`).
> Each service provides Swagger UI (the root path redirects automatically): `http://localhost:8081/` … `http://localhost:8087/`;
> the login request body is pre-filled with `ADMIN001/admin123`; the OpenAPI description lives at `/v3/api-docs`.
> List responses uniformly return `{"items": [...], "page": 0, "size": 10, "total": 42}`.
> Error responses uniformly return `{"code": "...", "message": "...", "details": [...]}`; HTTP semantics are in the conventions §4.

## 0. Seed Data Contract (must be identical across the initial 6 services)

### Customers (customer-service)

| customerId | Last / first name | City (addressLine3) | State | ZIP | FICO | Primary card |
|---|---|---|---|---|---|---|
| 000000001 | Kessler / Immanuel | Altenwerthshire | NY | 10001 | 720 | Y |
| 000000002 | Rosenbaum / Enrico | West Bernita | CA | 90002 | 680 | N |
| 000000003 | Homenick / Larry | New Gladys | TX | 75003 | 750 | N |
| 000000004 | DuBuque / Claire | Springfield | IL | 62704 | 800 | Y |
| 000000005 | Chen / Marcus | Seattle | WA | 98101 | 640 | N |
| 000000006 | Marino / Sofia | Miami | FL | 33101 | 710 | Y |
| 000000007 | Whitfield / Daniel | Denver | CO | 80202 | 590 | N |
| 000000008 | Bennett / Aisha | Chicago | IL | 60601 | 765 | N |

### Cards (card-service)

| cardNumber | accountId | customerId | Expiry date | Status | Embossed name |
|---|---|---|---|---|---|
| 0500024453765740 | 00000000001 | 000000001 | 2028-05-20 | Y | IMMANUEL KESSLER |
| 6677889900112233 | 00000000001 | 000000001 | 2029-01-01 | Y | IMANUEL KESSLER |
| 0683586198171516 | 00000000002 | 000000002 | 2029-08-11 | Y | ENRICO ROSENBAUM |
| 0923877193247330 | 00000000003 | 000000003 | 2027-01-10 | Y | LARRY HOMENICK |
| 1122334455667788 | 00000000004 | 000000004 | 2028-02-14 | Y | CLAIRE DUBUQUE |
| 2233445566778899 | 00000000005 | 000000005 | 2029-07-01 | Y | MARCUS CHEN |
| 3344556677889900 | 00000000006 | 000000006 | 2028-03-09 | Y | SOFIA MARINO |
| 4455667788990011 | 00000000007 | 000000007 | 2024-05-05 | N | DANIEL WHITFIELD |
| 5566778899001122 | 00000000008 | 000000008 | 2030-09-30 | Y | AISHA BENNETT |

### Users (iam-service)

| userId | Password (plaintext, seed only) | Type | First / last name |
|---|---|---|---|
| ADMIN001 | admin123 | A | System / Administrator |
| USER0001 | user1234 | U | Demo / User |

Passwords are stored as PBKDF2 hashes in the Flyway seed scripts (never in plaintext in the database).

### Transaction Reference Data (transaction-service)

| Type code | Description | Category code (within type) | Category description | Rate (group A000000000 / DEFAULT) |
|---|---|---|---|---|
| 01 | Purchase | 0001 | Regular Sales Draft | 18.00 / 20.00 |
| 01 | Purchase | 0002 | Regular Cash Advance | 24.00 / 20.00 |
| 01 | Purchase | 0003 | Convenience Check Debit | 24.00 / 20.00 |
| 01 | Purchase | 0004 | ATM Cash Advance | 24.00 / 20.00 |
| 01 | Purchase | 0005 | Interest Amount | 0.00 / 0.00 |
| 02 | Payment | 0002 | Payment | 0.00 / 0.00 |
| 03 | Credit | 0001 | Credit Adjustment | 0.00 / 0.00 |
| 04 | Authorization | 0001 | Authorization | 0.00 / 0.00 |
| 05 | Refund | 0001 | Refund | 0.00 / 0.00 |
| 06 | Reversal | 0001 | Reversal | 0.00 / 0.00 |
| 07 | Adjustment | 0001 | Adjustment | 0.00 / 0.00 |

Category accumulation (`tran_category_balance`) seed: account 1/01/0001 = 1200.00; account 1/01/0002 = 500.00;
account 2/01/0001 = 800.00; account 3/01/0001 = 400.00; account 4/01/0001 = 300.50;
account 6/01/0001 = 1500.00; account 8/01/0001 = 2000.00.

Transaction seed (`transaction`, ascending by transaction number):

| Transaction ID | Account | Card number | Type/category | Amount | Description | Processed timestamp |
|---|---|---|---|---|---|---|
| 0000000000000001 | 00000000001 | 0500024453765740 | 01/0001 | 50.47 | Purchase at Abshire-Lowe | 2026-09-15T10:00:00 |
| 0000000000000002 | 00000000002 | 0683586198171516 | 01/0001 | 91.90 | Return item at Nitzsche | 2026-09-16T11:30:00 |
| 0000000000000003 | 00000000003 | 0923877193247330 | 02/0002 | 1470.00 | BILL PAYMENT - ONLINE | 2026-09-20T09:00:00 |
| 0000000000000004 | 00000000001 | 0500024453765740 | 01/0002 | 200.00 | ATM Cash Advance | 2026-09-21T14:15:00 |
| 0000000000000005 | 00000000006 | 3344556677889900 | 01/0001 | 300.75 | Purchase at Ernser-Roob | 2026-09-22T16:45:00 |
| 0000000000000006 | 00000000008 | 5566778899001122 | 01/0001 | 500.00 | Purchase at Gleason | 2026-09-25T08:20:00 |

---

## 1. iam-service (:8081)

### POST /api/v1/auth/login

```json
{"userId": "ADMIN001", "password": "admin123"}
```

`200`:

```json
{
  "token": "<base64url(payload)>.<base64url(hmac)>",
  "expiresAt": "2026-10-26T00:29:46",
  "user": {"userId": "ADMIN001", "firstName": "System", "lastName": "Administrator",
           "userType": "A", "landingMenu": "ADMIN"}
}
```

- User ID/password are both `trim`med and the user ID is upper-cased; the password is not case-normalized (ADR-008).
- Failures uniformly return `401 INVALID_CREDENTIALS` (no distinction between an unknown user and a wrong password).
- Missing parameters → `400 VALIDATION_FAILED`.

### POST /api/v1/auth/verify

```json
{"token": "..."}
```

`200`: `{"valid": true, "userId": "ADMIN001", "userType": "A", "expiresAt": "..."}`
`200` (invalid/expired): `{"valid": false}` — does not return 401 (so the gateway can decide).

### GET /api/v1/users?page=0&size=10

`200`: `{"items": [UserResponse...], "page": 0, "size": 10, "total": 2}`

`UserResponse`: `{"userId","firstName","lastName","userType","version"}` (**does not include the password/hash**).

### GET /api/v1/users/{userId}

`200` UserResponse; `404 USER_NOT_FOUND`.

### POST /api/v1/users

```json
{"userId": "USER0002", "firstName": "Ada", "lastName": "Lovelace",
 "password": "pass1234", "userType": "U"}
```

`201` UserResponse; `409 USER_ALREADY_EXISTS`; `400 VALIDATION_FAILED` (user ID `^[A-Z0-9]{8}$`,
last/first name 1–20, password 8–64, type `[AU]`).

### PUT /api/v1/users/{userId}

```json
{"firstName": "Ada", "lastName": "Lovelace", "userType": "U", "password": "newpass1"}
```

`password` may be omitted (left unchanged). `200`; `404`; `409 LAST_ADMIN` (demoting the only administrator to U).

### DELETE /api/v1/users/{userId}

Optional request header `X-User-Id`. `204`; `404`; `409 LAST_ADMIN`; `409 CANNOT_DELETE_SELF`
(`X-User-Id` equals the target).

---

## 2. customer-service (:8082)

### GET /api/v1/customers?page=0&size=10

`200`: `{"items": [CustomerResponse...], "page": 0, "size": 10, "total": 8}`

`CustomerResponse`:

```json
{
  "customerId": "000000001",
  "firstName": "Immanuel", "middleName": "Madeline", "lastName": "Kessler",
  "addressLine1": "618 Deshaun Route", "addressLine2": "Apt. 802",
  "addressLine3": "Altenwerthshire",
  "stateCode": "NY", "countryCode": "USA", "zipCode": "10001",
  "phoneNumber1": "(212)555-0101", "phoneNumber2": null,
  "ssnMasked": "***-**-6789", "dateOfBirth": "1985-04-12",
  "eftAccountId": "0000000001", "primaryCardHolder": "Y",
  "ficoScore": 720, "version": 0
}
```

### GET /api/v1/customers/{customerId}

`200`; `404 CUSTOMER_NOT_FOUND`.

### POST /api/v1/customers

```json
{
  "customerId": "000000009", "firstName": "Grace", "middleName": "B",
  "lastName": "Hopper", "addressLine1": "1 Navy Way", "addressLine2": null,
  "addressLine3": "Arlington", "stateCode": "VA", "countryCode": "USA",
  "zipCode": "22201", "phoneNumber1": "7035550109", "phoneNumber2": null,
  "ssn": "123456789", "dateOfBirth": "1906-12-09", "eftAccountId": "0000000009",
  "primaryCardHolder": "N", "ficoScore": 800
}
```

`201`; `409 CUSTOMER_ALREADY_EXISTS`. `ssn` is write-only.

### PUT /api/v1/customers/{customerId}

Same fields as POST (`customerId` comes from the path; `ssn` is optional and omission means unchanged). `200`; `404`; `409`.

### Validation Error Codes

`400 VALIDATION_FAILED`; `details` preserves the legacy message, for example `stateCode: State: is not a valid state code`.

---

## 3. card-service (:8083)

### GET /api/v1/cards?accountId=00000000001&customerId=&page=0&size=7

All filters are optional; `accountId` is 11 digits and `customerId` is 9 digits.
`200`: `{"items": [CardResponse...], "page": 0, "size": 7, "total": 2}`

`CardResponse`: `{"cardNumber","accountId","customerId","embossedName","expiryDate","activeStatus","version"}`
(**does not return the CVV**).

### GET /api/v1/cards/{cardNumber}

`200` CardResponse; `404 CARD_NOT_FOUND`.

### GET /api/v1/cards/by-account/{accountId}

`200`: `{"items": [CardResponse...]}` (not paginated; used for internal calls and to compose the account view).

### POST /api/v1/cards

```json
{"cardNumber":"6677889900112233","accountId":"00000000002","customerId":"000000002",
 "cvv":"123","embossedName":"ENRICO ROSENBAUM","expiryDate":"2029-08-11","activeStatus":"Y"}
```

`201`; `409 CARD_ALREADY_EXISTS`; `409 ACCOUNT_CUSTOMER_MISMATCH` (the customer number differs from cards that already exist on the same account).

### PUT /api/v1/cards/{cardNumber}

```json
{"embossedName": "ENRICO ROSENBAUM", "activeStatus": "Y",
 "expiryMonth": 8, "expiryYear": 2029}
```

`200`; `404`; `409 CONCURRENT_UPDATE`; `400 VALIDATION_FAILED` (embossed name required and letters/spaces only,
status `[YN]`, month 1–12, year 1950–2099). **The CVV cannot be modified**.

---

## 4. account-service (:8084)

### GET /api/v1/accounts

`200`: `{"items": [AccountResponse...]}` (8 seed records).

### GET /api/v1/accounts/{accountId}

`200` AccountResponse; `404 ACCOUNT_NOT_FOUND`.

### PUT /api/v1/accounts/{accountId}

```json
{
  "activeStatus": "Y", "currentBalance": 1940.00, "creditLimit": 2020.00,
  "cashCreditLimit": 1020.00, "openDate": "2014-11-20",
  "expirationDate": "2025-05-20", "reissueDate": "2025-05-20",
  "currentCycleCredit": 0.00, "currentCycleDebit": 0.00,
  "addressZip": "10001", "groupId": "A000000000"
}
```

`200`; `404`; `400 VALIDATION_FAILED`; `409 CONCURRENT_UPDATE`.

### POST /api/v1/accounts/{accountId}/postings

```json
{"amount": 125.40}
```

`200` AccountResponse; `404`. Semantics: `currentBalance += amount`; `amount ≥ 0` is counted in `currentCycleCredit`,
otherwise in `currentCycleDebit` (BR-ACCT-09).

### POST /api/v1/accounts/{accountId}/payments

```json
{"amount": 1940.00}
```

`200`; `404`; `409 NOTHING_TO_PAY` (balance ≤ 0); `409 PAYMENT_EXCEEDS_BALANCE`; `409 INVALID_PAYMENT_AMOUNT`.

### POST /api/v1/accounts/{accountId}/interest-accruals

```json
{"amount": 25.50}
```

`200`; `404`. Semantics: `currentBalance += amount`, `currentCycleCredit = currentCycleDebit = 0`.

---

## 5. transaction-service (:8085)

### GET /api/v1/transactions?idPrefix=&cardNumber=&accountId=&page=0&size=10

`200`: `{"items": [TransactionResponse...], "page": 0, "size": 10, "total": 6}`, sorted ascending by `transactionId`.

`TransactionResponse`:

```json
{
  "transactionId": "0000000000000001",
  "accountId": "00000000001",
  "cardNumber": "0500024453765740",
  "typeCode": "01", "typeDescription": "Purchase",
  "categoryCode": "0001", "categoryDescription": "Regular Sales Draft",
  "source": "POS TERM", "description": "Purchase at Abshire-Lowe", "amount": 50.47,
  "merchantId": "800000001", "merchantName": "Abshire-Lowe",
  "merchantCity": "Springfield", "merchantZip": "10001",
  "originalTimestamp": "2026-09-15T10:00:00",
  "processedTimestamp": "2026-09-15T10:00:00"
}
```

### GET /api/v1/transactions/{transactionId}

`200`; `404 TRANSACTION_NOT_FOUND`.

### POST /api/v1/transactions

```json
{
  "accountId": "00000000002",
  "cardNumber": null,
  "typeCode": "01",
  "categoryCode": "0001",
  "source": "POS TERM",
  "description": "Purchase at Test Merchant",
  "amount": 25.99,
  "originalDate": "2026-10-25",
  "processedDate": "2026-10-25",
  "merchantId": "800000123",
  "merchantName": "Test Merchant",
  "merchantCity": "Springfield",
  "merchantZip": "10001"
}
```

`201` TransactionResponse (`transactionId = max + 1`, 16 digits);
`400 ACCOUNT_OR_CARD_REQUIRED`, `400 CARD_ACCOUNT_MISMATCH`, `400 VALIDATION_FAILED`,
`404 CARD_NOT_FOUND` (card number path), `404 ACCOUNT_NOT_FOUND`, `409 DUPLICATE_TRANSACTION_ID`.

### POST /api/v1/postings

```json
{
  "transactions": [
    {
      "transactionId": "0000000000000100",
      "cardNumber": "0500024453765740",
      "typeCode": "01", "categoryCode": "0001", "source": "POS TERM",
      "description": "Purchase at Merchant A", "amount": 75.00,
      "originalTimestamp": "2026-10-25T10:15:00",
      "merchantId": "800000001", "merchantName": "Merchant A",
      "merchantCity": "Springfield", "merchantZip": "10001"
    }
  ]
}
```

`200`:

```json
{
  "status": "COMPLETED_WITH_REJECTS",
  "processed": 1, "posted": 0, "rejected": 1,
  "rejections": [{"transactionId": "0000000000000100", "reasonCode": 102,
                  "reason": "OVERLIMIT TRANSACTION"}],
  "duplicates": []
}
```

`duplicates` lists transaction IDs that were submitted again and not posted twice (NR-POST-01).

Rules: card not found → 100; account not found → 101; `creditLimit < cycleCredit − cycleDebit + amount` → 102;
`account.expirationDate < originalTimestamp.date` → 103; if it passes, update the category accumulation, call account posting and write the transaction.
A repeated submission with the same `transactionId` → counted in `posted` and marked `duplicate=true` (idempotent, not posted twice).

### POST /api/v1/interest-runs

```json
{"statementDate": "2026-10-25"}
```

`200`:

```json
{"statementDate": "2026-10-25", "accountsProcessed": 7, "interestPosted": 3,
 "totalInterest": 45.50, "details": [{"accountId": "00000000001", "interestAmount": 25.50}]}
```

Rules: group `tran_category_balance` by account; for each `(account, type, category)` look up the disclosure-group rate (falling back to `DEFAULT` when missing),
`interest = balance × rate ÷ 1200` (HALF_UP, 2 decimals); after summing per account, generate one transaction (type 01, category 0005,
source `System`, description `Int. for a/c <accountId>`, card number taken from the account's first card) and call
`POST /accounts/{id}/interest-accruals`; the transaction ID = `yyyyMMdd` + an 8-digit sequence.

### POST /api/v1/bill-payments

```json
{"accountId": "00000000001", "confirm": true}
```

`200`: `{"transactionId": "0000000000000200", "amount": 1940.00, "newBalance": 0.00}`
`409 CONFIRMATION_REQUIRED` (`confirm=false`); `409 NOTHING_TO_PAY`; `404 ACCOUNT_NOT_FOUND`;
`503 DEPENDENCY_UNAVAILABLE`.

### GET /api/v1/reports/transactions?startDate=2026-09-01&endDate=2026-09-30&cardNumber=&page=0&size=50

`200`:

```json
{
  "items": [TransactionResponse...],
  "page": 0, "size": 50, "total": 6,
  "pageTotal": 2613.12,
  "accountTotals": [{"cardNumber": "0500024453765740", "total": 250.47}],
  "grandTotal": 2613.12
}
```

Filter: `processedTimestamp.date` inclusive range; optional `cardNumber`. Totals semantics: `pageTotal` = sum of the currently returned items;
`grandTotal` = sum of all items in the filter range; `accountTotals` = grouped by card number (BR-RPT-02/03).

### GET /api/v1/transaction-types

`200`: `{"items": [{"typeCode": "01", "description": "Purchase"}...]}`

### GET /api/v1/transaction-categories?typeCode=

`200`: `{"items": [{"typeCode": "01", "categoryCode": "0001", "description": "Regular Sales Draft"}...]}`

### PUT /api/v1/transaction-types/{typeCode}

```json
{"description": "Purchase"}
```

`200`; `404 TRANSACTION_TYPE_NOT_FOUND`.

---

## 6. authorization-service (:8086)

### POST /api/v1/authorizations

```json
{
  "cardNumber": "0500024453765740",
  "transactionAmount": 50.00,
  "authType": "01",
  "cardExpiryDate": "2805",
  "messageType": "0100",
  "messageSource": "POS",
  "processingCode": "000000",
  "merchantCategoryCode": "5411",
  "acquirerCountryCode": "USA",
  "posEntryMode": 5,
  "merchantId": "MERCHANT0000001",
  "merchantName": "Corner Grocery",
  "merchantCity": "Springfield",
  "merchantState": "IL",
  "merchantZip": "62704",
  "transactionId": "TXN000000000001",
  "authTimestamp": "2026-10-25T10:15:00"
}
```

Everything except `cardNumber` and `transactionAmount` is optional; `authTimestamp` defaults to the server's current time.

`201`:

```json
{
  "authorizationId": "5f0c...",
  "cardNumber": "0500024453765740",
  "transactionId": "TXN000000000001",
  "authTimestamp": "2026-10-25T10:15:00",
  "responseCode": "00",
  "reasonCode": "0000",
  "approvedAmount": 50.00,
  "accountId": "00000000001",
  "customerId": "000000001",
  "fraudFlag": "N"
}
```

On decline `responseCode=05`, `approvedAmount=0`, `reasonCode ∈ {3100,4100,4200,4300,5100,9000}`.
Downstream unavailable → `503 DEPENDENCY_UNAVAILABLE` (fail closed, no approval).

### GET /api/v1/pending-authorizations/{accountId}

`200`:

```json
{"accountId": "00000000001", "creditLimit": 2020.00, "cashLimit": 1020.00,
 "creditBalance": 50.00, "cashBalance": 0.00,
 "approvedCount": 1, "approvedAmount": 50.00,
 "declinedCount": 0, "declinedAmount": 0.00}
```

When there is no summary record, `404 AUTHORIZATION_SUMMARY_NOT_FOUND` is returned.
Available credit semantics (NR-AUTH-03): `creditLimit − the account's current balance − creditBalance`.

### GET /api/v1/authorizations?accountId=00000000001&page=0&size=10

`200`: `{"items": [AuthorizationSummary...], "page": 0, "size": 10, "total": 1}`,
sorted descending by `authTimestamp`.

`AuthorizationSummary`: `{"authorizationId","cardNumber","authTimestamp","transactionId",
"transactionAmount","responseCode","reasonCode","approvedAmount","merchantName","fraudFlag"}`

### GET /api/v1/authorizations/{authorizationId}

`200` full detail (including the request fields and `accountId/customerId/merchant*`); `404 AUTHORIZATION_NOT_FOUND`.

### POST /api/v1/authorizations/{authorizationId}/fraud

`200`: `{"authorizationId":"...","fraudFlag":"Y","cardNumber":"...","cardBlocked":true}`
`404`; repeated flags are idempotent.

### POST /api/v1/authorization-purges

```json
{"expiryDays": 5}
```

`200`:

```json
{"summariesRead": 3, "summariesDeleted": 1, "detailsRead": 5, "detailsDeleted": 4,
 "creditBalanceReleased": 120.00}
```

Rules: details whose `authTimestamp` is earlier than `now − expiryDays` (default 5) are deleted; when an approved detail is deleted,
`creditBalance -= approvedAmount` (NR-AUTH-01); the summary is recomputed from the remaining details, and when both the approved and declined counts are 0 the summary is deleted
(NR-AUTH-02).

---

## 7. Funds, Risk and Operations APIs

### 7.1 account-service Account Products and Fund Holds (A1.1)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/account-products` | Product list (including maximum limit, APR, monthly fee, statement day) |
| GET | `/api/v1/account-products/{productCode}` | Product detail |
| GET | `/api/v1/accounts/{accountId}/credit-position` | Limit, balance, holds, available credit |
| GET | `/api/v1/accounts/{accountId}/holds?status=ACTIVE` | Hold list; `status` is optional |
| POST | `/api/v1/accounts/{accountId}/holds` | Create a hold; `referenceId` is idempotent |
| DELETE | `/api/v1/accounts/{accountId}/holds/{holdId}` | Release a hold; repeated releases are idempotent |

Create hold request:

```json
{"amount": 100.00, "referenceType": "AUTHORIZATION", "referenceId": "auth-20261026-0001", "expiresAt": "2026-10-26T12:00:00"}
```

Available credit = `creditLimit - currentBalance - Σ(ACTIVE and non-expired holds)`.
Account responses gain a `productCode`; on account update the limit must not exceed the product maximum.

### 7.2 transaction-service Posting Engine (A1.2)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/posting-types` | Posting type list |
| GET | `/api/v1/posting-types/{typeCode}` | Type detail + debit/credit rules |
| POST | `/api/v1/posting-batches` | Create a posting batch (201, `idempotencyKey` idempotent) |
| GET | `/api/v1/posting-batches/{batchId}` | Batch detail (including legs) |
| GET | `/api/v1/posting-batches?page=&size=&accountId=&idempotencyKey=` | Paginated query |
| POST | `/api/v1/posting-batches/{batchId}/reversal` | Reversal; returns the reversal batch |

```json
{"idempotencyKey":"post-20261026-0001","typeCode":"CARD_PURCHASE",
 "accountId":"00000000006","amount":25.00,"currency":"USD",
 "description":"Purchase at Corner Grocery"}
```

Types: `CARD_PURCHASE`, `CASH_ADVANCE`, `FEE`, `INTEREST` (INCREASE); `PAYMENT`, `REFUND` (DECREASE);
`ADJUSTMENT` (INCREASE). A batch generates 2 legs and debits must balance credits; `reversal:<batchId>` is the idempotency key of the reversal batch.

### 7.3 ledger-service General Ledger (A1.3)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/ledger-accounts` | Chart of accounts |
| GET | `/api/v1/ledger-accounts/{code}` | Account detail |
| POST | `/api/v1/journal-entries` | Record a journal entry (201, `(sourceType, sourceId)` idempotent) |
| GET | `/api/v1/journal-entries/{entryId}` | Entry detail |
| GET | `/api/v1/journal-entries?page=&size=&sourceId=` | Paginated query |
| POST | `/api/v1/journal-entries/{entryId}/reversal` | Reversal (201) |
| GET | `/api/v1/trial-balance?period=YYYY-MM` | Trial balance |
| GET | `/api/v1/accounting-periods` | Period list |
| POST | `/api/v1/accounting-periods/{period}/close` | Close period |

```json
{"sourceType":"POSTING_BATCH","sourceId":"b-1","description":"Card purchase",
 "postedAt":"2026-10-26T04:00:00","currency":"USD",
 "lines":[{"accountCode":"1100","direction":"DR","amount":25.00},
          {"accountCode":"2100","direction":"CR","amount":25.00}]}
```

Seed accounts: 1000/1100/2000/2100/2200/4000/4100/5000/9000; seed periods: 2026-09 CLOSED, 2026-10/11 OPEN.

### 7.4 reconciliation-service Reconciliation and EOD (A1.4)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/reconciliation-runs` | Run reconciliation for a `businessDate` (201) |
| GET | `/api/v1/reconciliation-runs/{runId}` | Run detail + items |
| GET | `/api/v1/reconciliation-runs?page=&size=` | Run history |
| GET | `/api/v1/reconciliation-runs/{runId}/items` | Discrepancy details |
| POST | `/api/v1/reconciliation-items/{itemId}/resolve` | Record a discrepancy resolution note |
| POST | `/api/v1/eod-runs` | Run EOD (current step: reconciliation, 201) |
| GET | `/api/v1/eod-runs/{runId}` | EOD detail |
| GET | `/api/v1/eod-runs?page=&size=` | EOD history |

```json
{"businessDate":"2026-10-26"}
```

Match key: `posting_batch.batch_id` ↔ `journal_entry.source_id` (`sourceType=POSTING_BATCH`);
discrepancy statuses: `MATCH`, `MISSING_JOURNAL`, `MISSING_BATCH`, `AMOUNT_MISMATCH`, `UNBALANCED`.

### 7.5 payment-service Payments (P2)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/payment-orders` | Accept a payment (201, `idempotencyKey` idempotent) |
| GET | `/api/v1/payment-orders/{paymentId}` | Detail |
| GET | `/api/v1/payment-orders?page=&size=&status=&accountId=` | Pagination |
| POST | `/api/v1/payment-orders/{paymentId}/accept` | Accept |
| POST | `/api/v1/payment-orders/{paymentId}/settle` | Settle (posting) |
| POST | `/api/v1/payment-orders/{paymentId}/return` | Return |

```json
{"idempotencyKey":"pay-20261026-0001","debtorAccountId":"00000000006",
 "creditorName":"Acme Utility","creditorAccountNumber":"1234567890123456",
 "creditorBankCode":"BOFAUS3N","amount":120.00,"currency":"USD",
 "paymentType":"ACH","reference":"INV-2026-10","description":"Utility bill"}
```

### 7.6 billing-service Statements (P2)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/statements/generate` | Generate a statement (201, idempotent by account + period) |
| GET | `/api/v1/statements/{statementId}` | Detail |
| GET | `/api/v1/statements?accountId=&page=&size=` | Pagination |
| POST | `/api/v1/statements/{statementId}/pay` | Full payment |

```json
{"accountId":"00000000001","period":"2026-10"}
```

### 7.7 limit-service Limits (P2)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/limits` | Create a limit (201) |
| GET | `/api/v1/limits?scopeType=&scopeId=&page=&size=` | List |
| GET | `/api/v1/limits/{limitId}` | Detail |
| PUT | `/api/v1/limits/{limitId}` | Adjust the limit |
| POST | `/api/v1/limits/{limitId}/usage` | Hold |
| POST | `/api/v1/limits/{limitId}/release` | Release |
| GET | `/api/v1/limits/check?scopeType=&scopeId=&limitType=&currency=&amount=` | Availability check |

### 7.8 fx-service FX (P2)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/fx/rates` | FX rate list |
| POST | `/api/v1/fx/rates` | Maintain an FX rate (upsert) |
| POST | `/api/v1/fx/quotes` | Quote (201, valid for 5 minutes) |
| POST | `/api/v1/fx/quotes/{quoteId}/execute` | Execute conversion (201) |
| GET | `/api/v1/fx/conversions` | Conversion records |

### 7.9 P3/P4/P5 Extension APIs (by prefix)

> Complete fields are defined by each service's `/v3/api-docs`; the table below gives the contract entry points and key actions.

| Service | Path prefix | Key endpoints |
|---|---|---|
| loan | `/api/v1/loan-applications`, `/api/v1/loans` | apply, approve/reject, disburse, schedule, repayments |
| fraud | `/api/v1/fraud-evaluations`, `/api/v1/fraud-alerts` | scoring, alerts, confirm/dismiss |
| aml | `/api/v1/screenings`, `/api/v1/aml-alerts` | screening, alerts, clear/escalate |
| dispute | `/api/v1/disputes` | open case, review, provisional-credit, chargeback, resolve |
| collections | `/api/v1/delinquency-cases` | create case, recalculate, promise-to-pay, resolve |
| batch | `/api/v1/jobs`, `/api/v1/job-runs` | job definitions, execution, history |
| reporting | `/api/v1/reports`, `/api/v1/report-runs` | definitions, generation, result rows |
| notification | `/api/v1/notification-templates`, `/api/v1/notifications` | templates, creation, sending |
| document | `/api/v1/documents` | upload, metadata, content, archive, delete |
| workflow | `/api/v1/workflow-definitions`, `/api/v1/workflow-tasks` | definitions, create task, approve/reject |
| audit | `/api/v1/audit-events` | append, detail, search |
| reference | `/api/v1/reference` | list by domain, detail, create/update/delete |
| bff | `/api/v1/bff` | `GET /customer-overview?accountId=` |
| search | `/api/v1/search` | `GET ?q=&type=&limit=` |

## 8. Error Code Summary

| code | HTTP | Owner | Meaning |
|---|---|---|---|
| VALIDATION_FAILED | 400 | All | Parameter validation failed (`details` gives the field and the legacy message) |
| INVALID_CREDENTIALS | 401 | iam | Invalid user ID or password |
| USER_NOT_FOUND | 404 | iam | User does not exist |
| USER_ALREADY_EXISTS | 409 | iam | Duplicate user ID |
| LAST_ADMIN | 409 | iam | Deleting or demoting the last administrator is not allowed |
| CANNOT_DELETE_SELF | 409 | iam | Deleting yourself is not allowed |
| CUSTOMER_NOT_FOUND | 404 | customer | Customer does not exist |
| CUSTOMER_ALREADY_EXISTS | 409 | customer | Duplicate customer ID |
| CARD_NOT_FOUND | 404 | card | Card number does not exist |
| CARD_ALREADY_EXISTS | 409 | card | Duplicate card number |
| ACCOUNT_CUSTOMER_MISMATCH | 409 | card | Card ownership does not match the account/customer |
| ACCOUNT_NOT_FOUND | 404 | account | Account does not exist |
| PRODUCT_NOT_FOUND | 404 | account | Account product does not exist |
| PRODUCT_INACTIVE | 409 | account | Product discontinued |
| CREDIT_LIMIT_EXCEEDS_PRODUCT_MAX | 409 | account | Limit exceeds the product maximum |
| CASH_LIMIT_EXCEEDS_PRODUCT_MAX | 409 | account | Cash limit exceeds the product maximum |
| MISSING_PRODUCT_CODE | 409 | account | Account is missing a product code |
| ACCOUNT_INACTIVE | 409 | account | Account status is not Y; a hold cannot be placed |
| INVALID_HOLD_AMOUNT | 409 | account | Hold amount ≤ 0 |
| INVALID_HOLD_REFERENCE | 409 | account | Hold source type/idempotency key is empty |
| INVALID_HOLD_EXPIRY | 409 | account | Hold expiry is not in the future |
| INSUFFICIENT_AVAILABLE_CREDIT | 409 | account | Insufficient available credit |
| HOLD_NOT_FOUND | 404 | account | Hold record does not exist |
| HOLD_NOT_RELEASABLE | 409 | account | The current status cannot be released |
| HOLD_REFERENCE_CONFLICT | 409 | account | Idempotency key already used for a different amount |
| HOLD_REFERENCE_REUSED | 409 | account | Idempotency key already used for a closed record |
| POSTING_TYPE_NOT_FOUND | 404 | transaction | Posting type does not exist |
| POSTING_TYPE_INACTIVE | 409 | transaction | Posting type is inactive |
| INVALID_POSTING_AMOUNT | 409 | transaction | Posting amount ≤ 0 or precision exceeded |
| IDEMPOTENCY_KEY_REQUIRED | 400 | transaction | Missing idempotency key |
| IDEMPOTENCY_CONFLICT | 409 | transaction | Idempotency key already used for a different request |
| POSTING_BATCH_NOT_FOUND | 404 | transaction | Posting batch does not exist |
| UNBALANCED_POSTING | 409 | transaction | Debit/credit legs are unbalanced |
| REVERSAL_NOT_ALLOWED | 409 | transaction | A reversal batch cannot be reversed again |
| JOURNAL_ENTRY_NOT_FOUND | 404 | ledger | Journal entry does not exist |
| JOURNAL_SOURCE_CONFLICT | 409 | ledger | Business key already used for different content |
| UNBALANCED_JOURNAL | 409 | ledger | Journal debits and credits are unbalanced |
| INVALID_JOURNAL_AMOUNT | 409 | ledger | Entry amount ≤ 0 |
| INVALID_JOURNAL_ENTRY | 409 | ledger | Entry source/account is missing |
| DUPLICATE_LINE_NO | 409 | ledger | Duplicate entry line number |
| JOURNAL_ALREADY_REVERSED | 409 | ledger | Entry has already been reversed |
| LEDGER_ACCOUNT_NOT_FOUND | 404 | ledger | General ledger account does not exist |
| LEDGER_ACCOUNT_INACTIVE | 409 | ledger | General ledger account is inactive |
| PERIOD_NOT_FOUND | 404 | ledger | Accounting period does not exist |
| PERIOD_CLOSED | 409 | ledger | Accounting period is closed |
| PERIOD_ALREADY_CLOSED | 409 | ledger | Period already closed |
| RECONCILIATION_RUN_NOT_FOUND | 404 | reconciliation | Reconciliation run does not exist |
| RECONCILIATION_ITEM_NOT_FOUND | 404 | reconciliation | Reconciliation item does not exist |
| EOD_RUN_NOT_FOUND | 404 | reconciliation | EOD run does not exist |
| PAYMENT_NOT_FOUND | 404 | payment | Payment order does not exist |
| ACCOUNT_REQUIRED | 400 | payment | Missing debit account |
| ACCOUNT_INACTIVE | 409 | account/payment | Account status is not Y |
| IDEMPOTENCY_KEY_REQUIRED | 400 | transaction/payment | Missing idempotency key |
| IDEMPOTENCY_CONFLICT | 409 | transaction/payment | Idempotency key already used for a different request |
| INVALID_PAYMENT_AMOUNT | 409 | payment | Invalid payment amount |
| INVALID_PAYMENT_TYPE | 409 | payment | Invalid payment type |
| INVALID_PAYMENT_STATUS | 400 | payment | Invalid status filter value |
| PAYMENT_NOT_SCREENED | 409 | payment | Payment order is not SCREENED |
| PAYMENT_NOT_ACCEPTED | 409 | payment | Payment order is not ACCEPTED |
| PAYMENT_NOT_RETURNABLE | 409 | payment | Payment order cannot be returned |
| STATEMENT_NOT_FOUND | 404 | billing | Statement does not exist |
| STATEMENT_ALREADY_EXISTS | 409 | billing | Concurrent generation for the same account + period |
| INVALID_PERIOD | 400 | billing | Invalid period format |
| PRODUCT_NOT_FOUND | 404 | account/billing | Product does not exist |
| LIMIT_NOT_FOUND | 404 | limit | Limit does not exist |
| LIMIT_EXCEEDED | 409 | limit | Limit exceeded |
| LIMIT_ALREADY_DEFINED | 409 | limit | Limit already defined |
| LIMIT_INACTIVE | 409 | limit | Limit is inactive |
| LIMIT_BELOW_USED | 409 | limit | Adjusted limit is below the amount used |
| INVALID_LIMIT_AMOUNT | 409 | limit | Invalid limit amount |
| INVALID_LIMIT_RELEASE | 409 | limit | Invalid release amount |
| INVALID_LIMIT_SCOPE | 400 | limit | Invalid limit scope |
| INVALID_LIMIT_TYPE | 400 | limit | Invalid limit type |
| RATE_NOT_FOUND | 404 | fx | FX rate does not exist |
| RATE_CONFLICT | 409 | fx | FX rate concurrency conflict |
| RATE_INACTIVE | 409 | fx | FX rate is inactive |
| QUOTE_NOT_FOUND | 404 | fx | Quote does not exist |
| QUOTE_EXPIRED | 409 | fx | Quote has expired |
| QUOTE_ALREADY_CONSUMED | 409 | fx | Quote already consumed |
| INVALID_QUOTE_AMOUNT | 409 | fx | Invalid quote amount |
| INVALID_RATE | 409 | fx | Invalid FX rate |
| SAME_CURRENCY | 409 | fx | Source and target currency are the same |
| NOTHING_TO_PAY | 409 | account/transaction | Nothing owed; repayment is not possible |
| PAYMENT_EXCEEDS_BALANCE | 409 | account | Repayment amount exceeds the balance |
| INVALID_PAYMENT_AMOUNT | 409 | account | Repayment amount ≤ 0 |
| CONCURRENT_UPDATE | 409 | account/card | Optimistic locking conflict |
| TRANSACTION_NOT_FOUND | 404 | transaction | Transaction does not exist |
| DUPLICATE_TRANSACTION_ID | 409 | transaction | Duplicate transaction ID |
| ACCOUNT_OR_CARD_REQUIRED | 400 | transaction | At least one of account number and card number is required |
| CARD_ACCOUNT_MISMATCH | 400 | transaction | Card number does not match the account number |
| TRANSACTION_TYPE_NOT_FOUND | 404 | transaction | Transaction type does not exist |
| REPORT_REFERENCE_ERROR | 409 | transaction | Report referential integrity failure |
| CONFIRMATION_REQUIRED | 409 | transaction | Payment not confirmed |
| BILL_PAYMENT_CARD_NOT_FOUND | 409 | transaction | No card under the account; bill payment is not possible |
| AUTHORIZATION_NOT_FOUND | 404 | authorization | Authorization record does not exist |
| AUTHORIZATION_SUMMARY_NOT_FOUND | 404 | authorization | No pending authorization summary for this account |
| DEPENDENCY_UNAVAILABLE | 503 | transaction/authorization | Downstream service unavailable |
