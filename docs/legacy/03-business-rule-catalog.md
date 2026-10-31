# CardDemo Business Rule Catalog (with target system mapping)

> Each rule is annotated with: legacy evidence (program / paragraph / line number) → target microservice mapping → implementation status.
> Status: `Implemented` = runnable in the new code with tests; `Partial` = semantics preserved but simplified; `Not implemented` = explicitly deferred with the reason stated;
> `New` = a protection rule that the legacy system lacks and the target system adds.

## 1. Sign-on and user security (iam-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-SGN-01 | User ID and password are both mandatory; a missing value is rejected without querying | `COSGN00C.cbl:118-140` | `POST /api/v1/auth/login` parameter validation | Implemented |
| BR-SGN-02 | User ID and password are uniformly `UPPER-CASE`d before comparison | `COSGN00C.cbl:132-136` | `LoginService` uppercase normalization | Implemented |
| BR-SGN-03 | Password equality comparison (plain text in the legacy system); user-not-found and wrong-password are distinguishable | `COSGN00C.cbl:221-256` | PBKDF2 verification; errors unified as `INVALID_CREDENTIALS` | Partial (security improvement, see ADR-0008) |
| BR-SGN-04 | `SEC-USR-TYPE = 'A'` → administrator menu (COADM01C), otherwise the regular menu (COMEN01C) | `COSGN00C.cbl:230-240` | Sign-on response `landingMenu = ADMIN / USER` | Implemented |
| BR-SGN-05 | Only `A` grants entry to user administration; any other value is treated as a regular user | `COSGN00C.cbl:230-240` | Same as above | Implemented |
| BR-USER-01 | Adding a user: five fields are mandatory — last name, first name, user ID, password, and type | `COUSR01C.cbl:118-147` | `POST /api/v1/users` `@NotBlank` | Implemented |
| BR-USER-02 | User ID is 8 characters; duplicate user IDs are rejected | `CSUSR01Y.cpy:18`; `COUSR01C.cbl:260-266` | Primary key conflict → 409 `USER_ALREADY_EXISTS` | Implemented |
| BR-USER-03 | Password is 8 characters; the legacy system has no strength validation | `CSUSR01Y.cpy:21`; `COUSR01C.cbl:136-141` | 8-64 characters, non-blank; PBKDF2 storage | Partial (strength rules added) |
| BR-USER-04 | User type takes `A/U` (the legacy system only validates non-empty, which is a defect) | `COUSR01C.cbl:142-147`; `COSGN00C.cbl:230` | Enum validation `A/U` | Implemented (defect fixed) |
| BR-USER-05 | User list sorted by ascending user ID, 10 lines per page, PF7/PF8 paging | `COUSR00C.cbl:293-323,586-629` | `GET /api/v1/users?page&size=10` | Implemented |
| BR-USER-06 | Updating a user: five fields are mandatory, compared field by field, written only when something changed | `COUSR02C.cbl:180-245` | `PUT /api/v1/users/{id}` field validation | Implemented (a same-value PUT is idempotent and no longer an error) |
| BR-USER-07 | Deleting a user locates the record by user ID and returns a notice when it does not exist | `COUSR03C.cbl:144-192` | `DELETE /api/v1/users/{id}` → 404 | Implemented |
| NR-USER-01 | Deleting or demoting the last administrator is forbidden (the legacy system has no such protection and could leave the system without an administrator) | Defect description in `05-ubiquitous-language.md` §Risks; `COUSR03C.cbl` has no administrator count anywhere | `UserDirectoryService.delete/update` → 409 `LAST_ADMIN` | New |
| NR-USER-02 | Deleting the caller itself is forbidden (determined by the optional `X-User-Id` header) | None in the legacy system | Same as above → 409 `CANNOT_DELETE_SELF` | New |

## 2. Customer (customer-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-CUST-01 | Customer ID is 9 characters with leading zeros preserved | `CVCUS01Y.cpy:5` | `customerId` string of 9 characters | Implemented |
| BR-CUST-02 | First and last name are mandatory, only letters and spaces allowed, 25 characters each; middle name is optional | `COACTUPC.cbl:1560-1582,1898-1950,2012-2056` | `PUT /api/v1/customers/{id}` | Implemented |
| BR-CUST-03 | Address line 1 is mandatory (alphanumeric, 50); city is mandatory (the city is stored in `CUST-ADDR-LINE-3`, letters only) | `COACTUPC.cbl:1584-1630` | Field `addressLine3` (city) + validation | Implemented |
| BR-CUST-04 | State is a 2-character valid state code; ZIP is 5 digits and non-zero | `COACTUPC.cbl:2493-2510,2109-2175` | State code table + numeric validation | Implemented |
| BR-CUST-05 | Cross-validation of state + first 2 digits of ZIP (USPS derived table) | `COACTUPC.cbl:2536-2556`; `CSLKPCDY.cpy:1012-1073` | — | Not implemented (requires maintaining the full USPS table; deferred) |
| BR-CUST-06 | Phone 1/2 are optional; when filled, all three segments (3-3-4) must be non-zero digits and the area code must be in the NANPA list; stored format `(999)999-9999` | `COACTUPC.cbl:2225-2426` | 10-digit validation + formatting | Partial (the full NANPA area code list is not validated) |
| BR-CUST-07 | SSN segments 3-2-4 are all mandatory and non-zero; the first segment must not be 000/666/900-999 | `COACTUPC.cbl:2431-2488` | Validation + masked value only in responses | Implemented |
| BR-CUST-08 | FICO is 3 digits, non-zero, 300–850 | `COACTUPC.cbl:2514-2530` | `ficoScore` range validation | Implemented |
| BR-CUST-09 | Date of birth is a valid date and must not be later than today | `COACTUPC.cbl:1533-1543` | `dateOfBirth <= today` | Implemented |
| BR-CUST-10 | EFT account is 10 digits, non-zero | `COACTUPC.cbl:1648-1655` | Validation | Implemented |
| BR-CUST-11 | Primary cardholder flag is only `Y/N` | `COACTUPC.cbl:1657-1662` | Enum validation | Implemented |
| BR-CUST-12 | Government ID is 20 characters, nullable | `CVCUS01Y.cpy:18` | Length validation | Implemented |
| NR-CUST-01 | Create-customer API (the legacy system creates customers only through branch migration import) | `CBIMPORT.cbl:2300-customer` | `POST /api/v1/customers` | New |

## 3. Cards (card-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-CARD-01 | Card number is 16 digits and is the primary key; the card number→account/customer mapping is maintained by XREF | `CVACT02Y.cpy:4-6`; `CVACT03Y.cpy:4-9` | `card` + `card_xref` tables | Implemented |
| BR-CARD-02 | Card list paginated in ascending card number order (7 lines per page in the legacy system) | `COCRDLIC.cbl:177-178,1123-1261` | `GET /api/v1/cards?page&size=7` | Implemented (changed to offset pagination) |
| BR-CARD-03 | List filters: account number 11 digits, card number 16 digits; both conditions combined with AND | `COCRDLIC.cbl:1003-1067,1382-1405` | Query parameter validation | Implemented |
| BR-CARD-04 | Row action code allows only `S`/`U` and only one row can be selected on a screen | `COCRDLIC.cbl:1073-1121` | List row `action` field validation (kept for the front end) | Partial (the backend returns the available actions) |
| BR-CARD-05 | Card details are read by card number primary key (card/account ownership is not validated, which is a legacy defect) | `COCRDSLC.cbl:726-773` | `GET /api/v1/cards/{cardNumber}` | Implemented (ownership validation capability added) |
| BR-CARD-06 | Account number and card number are mandatory and must be well-formed when querying | `COCRDSLC.cbl:647-715` | Parameter validation | Implemented |
| BR-CARD-07 | Cardholder name is mandatory, only letters and spaces, 50 characters | `COCRDUPC.cbl:806-840` | `PUT /api/v1/cards/{cardNumber}` | Implemented |
| BR-CARD-08 | Card status is only `Y/N` | `COCRDUPC.cbl:845-873` | Enum validation | Implemented |
| BR-CARD-09 | Expiry month 1–12, expiry year 1950–2099; the day is not editable | `COCRDUPC.cbl:877-944` | Month/year validation, day keeps the original value | Implemented |
| BR-CARD-10 | Concurrency comparison before saving (CVV/name/year/month/day/status); rollback on mismatch | `COCRDUPC.cbl:1498-1520` | `@Version` optimistic locking → 409 `CONCURRENT_UPDATE` | Implemented (mechanism replaced) |
| BR-CARD-11 | When nothing changed, a notice is shown and nothing is written | `COCRDUPC.cbl:680-693` | PUT is idempotent and does not raise an error | Partial (REST semantics improved) |
| NR-CARD-01 | The CVV must not be overwritten when a card is rewritten (the legacy system writes blanks/zeros into the CVV, which is a defect) | `COCRDUPC.cbl:1464-1465` | Domain methods change only permitted fields | New (defect fix) |
| NR-CARD-02 | Card responses must not return the CVV | The legacy screen has no CVV; DB2/VSAM do | Response DTO excludes `cvv` | New (data minimization) |

## 4. Accounts (account-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-ACCT-01 | Account number is 11 characters, non-zero, leading zeros preserved | `COACTUPC.cbl:1787-1813`; `CVACT01Y.cpy:5` | 11-character validation of the path variable | Implemented |
| BR-ACCT-02 | Account status is only `Y/N` | `COACTUPC.cbl:1856-1893` | `Account.applyDetails` | Implemented |
| BR-ACCT-03 | Current balance, credit limit, cash limit, and current cycle credit/debit are all mandatory (`S9(10)V99`) | `COACTUPC.cbl:2180-2219` | `@NotNull` + domain validation | Implemented |
| BR-ACCT-04 | Open/expiry/reissue dates are mandatory in the format `YYYY-MM-DD`, with century 19/20 and a real calendar date | `COACTUPC.cbl:1478-1507`; `CSUTLDPY.cpy:18-330` | `LocalDate` + year 1900–2099 | Implemented (calendar validity guaranteed by `java.time`) |
| BR-ACCT-05 | When nothing changed in account maintenance, a notice is shown and nothing is written | `COACTUPC.cbl:1205-COMPARE-OLD-NEW` | PUT is idempotent | Partial (REST semantics improved) |
| BR-ACCT-06 | Concurrent updates: roll back when the refresh snapshot differs from the database | `COACTUPC.cbl:4109-4191` | `@Version` optimistic locking | Implemented (mechanism replaced) |
| BR-ACCT-07 | The account number → customer number/card number mapping goes through CXACAIX (the CCXREF account alternate index) | `COACTVWC.cbl:723-770` | `by-account` query provided by card-service | Implemented (responsibility moved) |
| BR-ACCT-08 | The account view shows account + customer + card number (spanning two aggregates) | `COACTVWC.cbl:471-525` | Front end/BFF composes three calls: `account + customer + card` | Partial (changed to a composed query, see ADR-0004) |
| BR-ACCT-09 | Accounting: balance += amount; an amount ≥ 0 is added to the current cycle credit, otherwise to the current cycle debit | `CBTRN02C.cbl:547-552` | `POST /api/v1/accounts/{id}/postings` | Implemented |
| BR-ACCT-10 | Bill payment: only reduces the balance, without changing the current cycle credit/debit | `COBIL00C.cbl:234`; `UPDATE-ACCTDAT-FILE` | `POST /api/v1/accounts/{id}/payments` | Implemented |
| BR-ACCT-11 | Interest settlement: balance += accumulated interest, zero the current cycle credit/debit | `CBACT04C.cbl:350-354` | `POST /api/v1/accounts/{id}/interest-accruals` | Implemented |
| NR-ACCT-01 | The repayment amount must be > 0 and must not exceed the current balance (in the legacy system the amount always equals the balance, so this never triggers) | `COBIL00C.cbl:224,234` | `Account.applyBillPayment` | New (protection) |

## 5. Transaction entry and query (transaction-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-TRN-01 | At least one of account number or card number must be entered; when both are given, the account is resolved from the card number and checked for consistency | `COTRN02C.cbl:193-230` | `POST /api/v1/transactions` | Implemented |
| BR-TRN-02 | The account/card number must exist in XREF | `COTRN02C.cbl:576-637` | Calls card-service, 404 → 400 | Implemented |
| BR-TRN-03 | Type, category, source, description, amount, original date, processing date, merchant ID/name/city/ZIP are mandatory | `COTRN02C.cbl:251-317` | `@NotBlank`/`@NotNull` | Implemented |
| BR-TRN-04 | Type, category, and merchant ID must be numeric | `COTRN02C.cbl:322-337,430-436` | Regex/numeric validation | Implemented |
| BR-TRN-05 | Amount format `-99999999.99` (sign + 8 integer digits + 2 decimals) | `COTRN02C.cbl:339-351` | `BigDecimal`, with server-side range and decimal place validation | Implemented (changed to a numeric protocol) |
| BR-TRN-06 | Original/processing dates use the format `YYYY-MM-DD` and must be valid dates | `COTRN02C.cbl:353-427` | `LocalDate` + format | Implemented (calendar validity guaranteed by `java.time`) |
| BR-TRN-07 | A new transaction number = current maximum transaction number + 1 (16 characters) | `COTRN02C.cbl:442-451` | `TransactionIdGenerator` (database max+1) | Implemented |
| BR-TRN-08 | Duplicate transaction numbers are rejected | `COTRN02C.cbl:735-741` | Primary key conflict → 409 `DUPLICATE_TRANSACTION_ID` | Implemented |
| BR-TRN-09 | Transaction list sorted by ascending transaction number, 10 lines per page; can be filtered by transaction number prefix | `COTRN00C.cbl:591-654,206-218` | `GET /api/v1/transactions?page&size&idPrefix` | Implemented |
| BR-TRN-10 | Transaction details show all fields (description truncated at 60, merchant name at 30, city at 25 are screen limits) | `COTRN01C.cbl:178-190` | `GET /api/v1/transactions/{id}` returns all fields | Implemented |

## 6. End-of-day posting (transaction-service + account-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-POST-01 | Card number does not exist → reject code 100 `INVALID CARD NUMBER FOUND` | `CBTRN02C.cbl:380-392` | `PostingService` | Implemented |
| BR-POST-02 | Account does not exist → reject code 101 `ACCOUNT RECORD NOT FOUND` | `CBTRN02C.cbl:393-399` | Same as above | Implemented |
| BR-POST-03 | `available credit < current cycle credit − current cycle debit + this transaction` → reject code 102 `OVERLIMIT TRANSACTION` | `CBTRN02C.cbl:403-413` | Same as above (calls the account-service snapshot) | Implemented |
| BR-POST-04 | Account expiry date < transaction original date → reject code 103 `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` | `CBTRN02C.cbl:414-420` | Same as above | Implemented |
| BR-POST-05 | Rejections are written to the rejection details (formerly DALYREJS), recording the failure reason code and description | `CBTRN02C.cbl:446-465` | `daily_transaction.reject_reason` + run result | Implemented (storage medium replaced) |
| BR-POST-06 | On passing validation: update the transaction category accumulator (create it when no record exists), update the account balance and current cycle credit/debit, and write the transaction master record | `CBTRN02C.cbl:424-444,467-559` | `PostingService` + `POST /accounts/{id}/postings` | Implemented |
| BR-POST-07 | The processing timestamp is generated at posting time; the original timestamp keeps the requested value | `CBTRN02C.cbl:436-438` | `processedAt = now()` | Implemented |
| BR-POST-08 | The batch returns code 4 when rejections occurred (partial success semantics) | `CBTRN02C.cbl:229-231` | Run result `status = COMPLETED_WITH_REJECTS` | Implemented |
| NR-POST-01 | Single posting is idempotent: repeated submission of the same `transactionId` returns the existing result | None in the legacy system (the VSAM primary key naturally rejects it) | Unique constraint + idempotent response | New |

## 7. Interest (transaction-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-INT-01 | Monthly interest = `category balance × annual rate ÷ 1200` | `CBACT04C.cbl:462-467` | `InterestService` | Implemented |
| BR-INT-02 | The rate comes from the disclosure group (account group + type + category); when missing, the `DEFAULT` group is used | `CBACT04C.cbl:415-460` | `DisclosureGroupRepository` falls back to `DEFAULT` | Implemented |
| BR-INT-03 | No interest transaction is produced when the rate is 0 | `CBACT04C.cbl:214-217` | Same as above | Implemented |
| BR-INT-04 | Each account accrues interest per category, aggregates it into a single interest transaction (type 01/category 0005, source System, description `Int. for a/c <acct>`), and zeroes the current cycle credit/debit | `CBACT04C.cbl:462-500,350-354` | `InterestService` + `interest-accruals` | Implemented |
| BR-INT-05 | Fee calculation (`1400-COMPUTE-FEES`) | `CBACT04C.cbl:518-520` (empty implementation) | — | Not implemented (not implemented in the legacy system; requirement pending clarification) |

## 8. Bill payment (transaction-service orchestration + account-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-BILL-01 | Reject when balance ≤ 0: `You have nothing to pay...` | `COBIL00C.cbl:197-206` | `BillPaymentService` → 409 `NOTHING_TO_PAY` | Implemented |
| BR-BILL-02 | Explicit confirmation (`Y`) is required; `N` clears the screen and exits; any other value raises an error | `COBIL00C.cbl:173-191` | Request body `confirm` boolean | Implemented (protocol adaptation) |
| BR-BILL-03 | Payment amount = current balance (full repayment) | `COBIL00C.cbl:224` | `BillPaymentService` reads the account balance | Implemented |
| BR-BILL-04 | Generates a transaction: type 02, category 0002, source `POS TERM`, description `BILL PAYMENT - ONLINE`, merchant `999999999/BILL PAYMENT/N/A/N/A`, timestamps set to the current time | `COBIL00C.cbl:218-233` | Same as above | Implemented |
| BR-BILL-05 | Balance deduction and transaction persistence must be consistent (in the legacy system they are in the same CICS transaction) | `COBIL00C.cbl:233-235` | Local transaction + remote call, rollback on failure (see ADR-0005) | Implemented |

## 9. Authorization (authorization-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-AUTH-01 | The card number must exist in XREF, otherwise decline 3100 | `COPAUA0C.cbl:472-513,699-704` | `AuthorizationService` | Implemented |
| BR-AUTH-02 | The account/customer must exist, otherwise decline 3100 | `COPAUA0C.cbl:520-560,560-610` | Same as above | Implemented |
| BR-AUTH-03 | Available credit: in the legacy system `credit limit − credit balance` (summary exists) / `credit limit − current balance` (no summary); this transaction > available credit → decline 4100. In the legacy system the account debt is no longer deducted once the summary is created, which is a limit amplification defect (NR-AUTH-03) | `COPAUA0C.cbl:660-683,705-706` | `AuthorizationPolicy.availableCredit` (corrected to deduct both) | Partial (defect fixed) |
| BR-AUTH-04 | Card not active → decline 4200 (the legacy branch is dead code) | `COPAUA0C.cbl:707-708`; the 88 condition is never SET | Same as above (card.activeStatus) | Implemented (intent completed) |
| BR-AUTH-05 | Account closed (status ≠ Y) → decline 4300 (legacy dead code) | `COPAUA0C.cbl:709-710` | Same as above | Implemented (intent completed) |
| BR-AUTH-06 | Card-level fraud flag → decline 5100 (legacy dead code) | `COPAUA0C.cbl:711-712` | `card_fraud_flag` | Implemented (intent completed) |
| BR-AUTH-07 | Merchant fraud → decline 5200; the legacy system has no decision data source | `COPAUA0C.cbl:713-714` | — | Not implemented (U4, no data source) |
| BR-AUTH-08 | Approved: response code `00`, approved amount = transaction amount, reason `0000`; declined: response code `05`, approved amount 0 | `COPAUA0C.cbl:685-698` | Decision response | Implemented |
| BR-AUTH-09 | Reason code priority: not found (3100) > limit (4100) > card not active (4200) > account closed (4300) > card fraud (5100) > other (9000) | `COPAUA0C.cbl:699-718` | Decision order | Implemented |
| BR-AUTH-10 | Summary update: approved → count +1, amount +, credit balance +; declined → count +1, amount + | `COPAUA0C.cbl:813-822` | `AuthSummary` | Implemented |
| BR-AUTH-11 | Each authorization writes an authorization detail (all request fields + response + fraud flag) | `COPAUA0C.cbl:786-850`; `AUTHFRDS.ddl` | `auth_detail` table | Implemented |
| BR-AUTH-12 | Authorization query: view summaries and details by account; a single authorization can be flagged as fraud | `COPAUS0C/1C/2C` functional comments; `AUTHFRDS.AUTH_FRAUD` | `GET /authorizations`, `POST /{card}/{ts}/fraud` | Implemented |
| BR-AUTH-13 | Expired authorization cleanup: details with an age ≥ the expiry days (default 5) are deleted; a summary without details is deleted | `CBPAUP0C.cbl:156,196-209,280-346` | `POST /api/v1/authorization-purges` | Implemented |
| NR-AUTH-01 | The credit balance must be rolled back when expired authorizations are cleaned up (the legacy system does not roll it back, which causes a limit leak) | `CBPAUP0C.cbl:284-296` | `credit_balance -= approved_amount` on cleanup and recompute the summary | New (defect fix) |
| NR-AUTH-02 | The summary cleanup condition is changed to "approved count and declined count are both 0" (the legacy system checks the approved count twice) | `CBPAUP0C.cbl:156` | Same as above | New (defect fix) |
| NR-AUTH-03 | Available credit must deduct both the account's current debt and unexpired approved holds (the legacy system ignores the account debt once a summary exists, so the first authorization amplifies the limit) | `COPAUA0C.cbl:665-683,813-817` | `AuthorizationPolicy.availableCredit` | New (defect fix) |

## 10. Reports (transaction-service)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-RPT-01 | Report types: monthly (first/last day of the month)/annual (1-1 to 12-31)/custom | `CORPT00C.cbl:212-256` | `GET /reports/transactions?startDate&endDate` | Implemented (the date range is passed in directly) |
| BR-RPT-02 | Filter by processing date over a closed interval | `CBTRN03C.cbl:173-178` | Query condition | Implemented |
| BR-RPT-03 | Group by card number, paginate every 20 lines, subtotal at three levels (page/account/grand total) | `CBTRN03C.cbl:181-203,282-322` | Result contains `pageTotal/accountTotal/grandTotal` | Partial (paper pagination not implemented, subtotal semantics retained) |
| BR-RPT-04 | Referential integrity: the report fails when the card number/type/category does not exist | `CBTRN03C.cbl:484-512` | Returns 409 `REPORT_REFERENCE_ERROR` | Implemented |
| BR-RPT-05 | Amount format `-ZZZ,ZZZ,ZZZ.ZZ` | `CVTRA07Y.cpy:15-31` | JSON numeric values + CSV export formatted by the front end | Not implemented (layout) |

## 11. Migration and integration (deferred items)

| Rule | Rule description | Legacy evidence | Target mapping | Status |
|---|---|---|---|---|
| BR-MIG-01 | Export order C→A→X→T→D, 500-byte records, increasing sequence number | `CBEXPORT.cbl:151-157` | Migration tool (standalone job) | Not implemented (this iteration; separate migration project) |
| BR-MIG-02 | Import dispatches by record type and writes unknown types to the error file | `CBIMPORT.cbl:272-285` | Same as above | Not implemented (this iteration) |
| BR-MQ-01 | MQ `INQA` + 11-digit account number queries account details and echoes MSGID/CORRELID unchanged | `COACCT01.cbl:105-112,393-426` | Can be mapped to `GET /api/v1/accounts/{id}`; the MQ adapter is developed separately | Partial (HTTP is available, the MQ adapter is deferred) |
| BR-MQ-02 | MQ date query returns the system date and time | `CODATE01.cbl:339-361` | `GET /actuator/info`/a new utility API | Not implemented (non-core) |
| BR-TT-01 | Transaction type/category maintenance (list/add/update/delete, DB2 cursor pagination) | `COTRTLIC.cbl`, `COTRTUPC.cbl`, `COBTUPDT.cbl` | `GET /api/v1/transaction-types`, `/transaction-categories`, `PUT /transaction-types/{code}` | Partial (query and description maintenance only; category add/delete deferred) |
