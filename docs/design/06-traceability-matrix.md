# Requirements–Design–Implementation Traceability Matrix (Legacy Programs → Microservices)

> Status: `Implemented` = the new service provides equivalent capability with tests; `Partial` = semantics preserved but the UI/layout/batch form changed;
> `Deferred` = not implemented in this phase with a stated reason; `Replaced` = superseded by platform capability.
> Rule IDs are in `docs/legacy/03-business-rule-catalog.md`; interfaces are in `docs/design/05-api-catalog.md`.

## 1. Online Programs (24)

| Legacy program | Function | Target service | Target endpoint / class | Rules | Status |
|---|---|---|---|---|---|
| COSGN00C | Login and menu routing | iam | `POST /api/v1/auth/login`, `/auth/verify` | BR-SGN-01..05, ADR-008 | Implemented |
| COADM01C | Administrator menu | iam | `GET/POST/PUT/DELETE /api/v1/users` (menu items rendered by the frontend) | BR-USER-* | Implemented |
| COMEN01C | Regular user menu | iam + each service | Menu items are rendered by the frontend based on `landingMenu` | — | Partial (menu data maintained by the frontend) |
| COUSR00C | User list | iam | `GET /api/v1/users` | BR-USER-05 | Implemented |
| COUSR01C | Create user | iam | `POST /api/v1/users` | BR-USER-01..04 | Implemented |
| COUSR02C | Update user | iam | `PUT /api/v1/users/{userId}` | BR-USER-06, BR-USER-04 | Implemented |
| COUSR03C | Delete user | iam | `DELETE /api/v1/users/{userId}` | BR-USER-07, NR-USER-01/02 | Implemented |
| COACTVWC | Account view (account + customer) | account + customer + card | `GET /accounts/{id}` + `GET /customers/{cid}` + `GET /cards/by-account/{aid}` | BR-ACCT-07/08, BR-CARD-05 | Implemented (composed query) |
| COACTUPC | Account + customer maintenance | account + customer | `PUT /accounts/{id}`, `PUT /customers/{id}` | BR-ACCT-01..06, BR-CUST-02..12 | Implemented (split into two use cases) |
| COCRDLIC | Card list (7 rows per page) | card | `GET /api/v1/cards?page&size=7` | BR-CARD-02..04 | Implemented (offset pagination) |
| COCRDSLC | Card detail | card | `GET /api/v1/cards/{cardNumber}` | BR-CARD-05/06 | Implemented |
| COCRDUPC | Card maintenance | card | `PUT /api/v1/cards/{cardNumber}` | BR-CARD-07..11, NR-CARD-01/02 | Implemented |
| COTRN00C | Transaction list (10 rows per page) | transaction | `GET /api/v1/transactions` | BR-TRN-09 | Implemented |
| COTRN01C | Transaction detail | transaction | `GET /api/v1/transactions/{id}` | BR-TRN-10 | Implemented |
| COTRN02C | Transaction entry | transaction | `POST /api/v1/transactions` | BR-TRN-01..08 | Implemented |
| COBIL00C | Bill payment | transaction + account | `POST /api/v1/bill-payments` → `POST /accounts/{id}/payments` | BR-BILL-01..05, NR-ACCT-01 | Implemented |
| CORPT00C | Report parameters and submission | transaction | `GET /api/v1/reports/transactions` | BR-RPT-01..03 | Partial (does not generate JCL) |
| COPAUA0C | Authorization decision | authorization | `POST /api/v1/authorizations` | BR-AUTH-01..11 | Implemented |
| COPAUS0C | Pending authorization summary view | authorization | `GET /api/v1/pending-authorizations/{accountId}` | BR-AUTH-12 | Implemented |
| COPAUS1C | Authorization detail view | authorization | `GET /api/v1/authorizations/{id}` | BR-AUTH-12 | Implemented |
| COPAUS2C | Flag authorization as fraud | authorization | `POST /api/v1/authorizations/{id}/fraud` | BR-AUTH-12, BR-AUTH-06 | Implemented |
| COTRTLIC | Transaction type/category list | transaction | `GET /api/v1/transaction-types`, `/transaction-categories` | BR-TT-01 | Partial (read-only + type description maintenance) |
| COTRTUPC | Transaction type/category maintenance | transaction | `PUT /api/v1/transaction-types/{code}` | BR-TT-01 | Partial (category create/delete deferred) |
| COBTUPDT | Transaction type update (DB2) | transaction | Same as above | BR-TT-01 | Partial |

## 2. Batch and Utility Programs (20)

| Legacy program | Function | Target service / replacement | Rules | Status |
|---|---|---|---|---|
| CBTRN02C | End-of-day posting | transaction `POST /api/v1/postings` + account `postings` | BR-POST-01..08, NR-POST-01 | Implemented |
| CBTRN01C | Daily transaction validation (read-only) | the validation stage of transaction `POST /api/v1/postings` | BR-POST-01..04 | Implemented (no longer printed separately) |
| CBTRN03C | Transaction detail report | transaction `GET /api/v1/reports/transactions` | BR-RPT-01..05 | Partial (JSON, no 133-byte layout) |
| CBACT04C | Interest accrual | transaction `POST /api/v1/interest-runs` + account `interest-accruals` | BR-INT-01..04 | Implemented |
| CBPAUP0C | Expired authorization purge | authorization `POST /api/v1/authorization-purges` | BR-AUTH-13, NR-AUTH-01/02 | Implemented |
| CBSTM03A | Statement generation (text + HTML) | — | — | Deferred (reporting platform, U5) |
| CBSTM03B | Statement layout subroutine | — | — | Deferred |
| CBEXPORT | Branch migration export | migration tool | BR-MIG-01 | Deferred |
| CBIMPORT | Branch migration import | migration tool + customer `POST` | BR-MIG-02 | Deferred |
| DBUNLDGS | IMS unload to GSAM | migration tool | — | Deferred |
| PAUDBUNL | IMS authorization database unload | migration tool (migrates only the last 5 days, U2) | — | Deferred |
| PAUDBLOD | IMS authorization database load | migration tool | — | Deferred |
| CBACT01C | Account printing | account `GET /accounts` + ops export | — | Partial (printing implemented by ops tooling) |
| CBACT02C | Card printing | card `GET /cards` + ops export | — | Partial |
| CBACT03C | Cross-reference printing | card `GET /cards` | — | Partial |
| CBCUS01C | Customer printing | customer `GET /customers` | — | Partial |
| CSUTLDTC | Date validation subroutine | `java.time` + DTO validation | BR-ACCT-04, BR-TRN-06 | Replaced |
| COBSWAIT | Batch wait | Not needed (replaced by JCL scheduling) | — | Replaced |
| COACCT01 / CODATE01 | MQ account/date lookup | account `GET /accounts/{id}` (MQ adapter deferred) | BR-MQ-01/02 | Partial |

> Note: the capabilities of `COBSWAIT` and `CSUTLDTC` are replaced by the platform/language and do not get their own services; the **data semantics**
> of the printing programs are already covered by the query APIs, and paper layout is not migrated (U5).

## 3. Rules → Implementation → Tests

| Rule group | Implementation classes | Tests |
|---|---|---|
| BR-SGN-01..05, NR-USER-01/02 | `iam.application.AuthenticationService`, `UserDirectoryService` | `AuthenticationServiceTest`, `UserControllerTest` |
| BR-USER-01..07 | `UserDirectoryService` | `UserDirectoryServiceTest`, `UserControllerTest` |
| BR-CUST-02..12, NR-CUST-01 | `CustomerService`, `domain.model.Customer` | `CustomerTest`, `CustomerControllerTest` |
| BR-CARD-01..11, NR-CARD-01/02 | `CardService`, `domain.model.Card` | `CardTest`, `CardControllerTest` |
| BR-ACCT-02..06, BR-ACCT-09..11, NR-ACCT-01 | `AccountApplicationService`, `Account` | `AccountTest`, `AccountControllerTest` |
| BR-TRN-01..08, BR-POST-01..08, NR-POST-01 | `TransactionApplicationService`, `PostingService`, `PostingPolicy` | `PostingPolicyTest`, `TransactionControllerTest` |
| BR-INT-01..04 | `InterestService`, `InterestCalculator` | `InterestCalculatorTest`, `InterestServiceTest` |
| BR-BILL-01..05 | `BillPaymentService` | `BillPaymentServiceTest` |
| BR-RPT-01..04 | `TransactionReportService` | `TransactionReportServiceTest` |
| BR-AUTH-01..13, NR-AUTH-01/02/03 | `AuthorizationService`, `AuthorizationPolicy`, `AuthorizationPurgeService` | `AuthorizationPolicyTest`, `AuthorizationControllerTest` |

## 5. New Capability: Premium Card Installment Cashback

| Capability | Target service / endpoint | Rules | Tests |
|---|---|---|---|
| Campaign duration and product targeting | marketing `POST /campaigns` | PC-01/02 | `CampaignTest` |
| Enrollment | marketing `POST /campaigns/{id}/enrollments` | PC-04/05 | `EnrollmentServiceTest` |
| Spend cashback | marketing `POST /reward-evaluations` | PC-03/06/07/08/09 | `RewardServiceTest`, `RewardCalculatorTest` |
| Cashback posting/retry | account `POST /accounts/{id}/credits`; marketing `/rewards/{id}/retry` | PC-10 | `RewardServiceTest`, `AccountControllerTest` |
| Transaction trigger | transaction `PostingService` → marketing | a failure does not affect posting | `PostingServiceTest` |
| Front end | web-ui `#/premium-offers`, `#/premium-campaigns` | — | `node --check` + API integration testing |

## 6. Coverage Self-Check

| Check | Result |
|---|---|
| Does every one of the 44 legacy programs have a destination? | 44/44 (24 online + 20 batch/utility, see §1/§2) |
| Are the core-domain programs (authorization/posting/interest/bill payment/account maintenance) implemented equivalently? | 5/5 |
| Does every declared business rule have an implementation or an explicit deferral reason? | 100% |
| Does each deferral need business confirmation? | Yes (U4 merchant fraud, U5 report layout, BR-MIG, BR-MQ, BR-INT-05 fees) |
| Are there untracked legacy data fields? | No (see `docs/legacy/04-data-dictionary.md`; the card CVV is stored but never exposed) |
