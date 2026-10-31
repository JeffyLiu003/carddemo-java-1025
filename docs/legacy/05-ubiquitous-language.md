# 05 Ubiquitous Language Glossary (CardDemo Legacy System → New System)

**Evidence convention**: the 4th column `file:line` gives real lines of the `carddemo-cobol` source; the 5th column is a **design recommendation for the new system** (not a legacy fact, with no source evidence) and is used only to align legacy semantics with domain concepts. Wherever a concept genuinely does not exist in the legacy system, the 4th column says "no legacy counterpart (see §3)".

## 1. Core glossary

| English term | Translated label | Definition | Legacy implementation evidence | Corresponding new-system concept/service (recommended) |
|---|---|---|---|---|
| Customer | Customer | A natural person holding a credit card account, identified by a 9-digit CUST-ID; the record contains name, 3 address lines, SSN, and FICO score | `CVCUS01Y.cpy:4-22`, `CUSTFILE.jcl:46-52`, `COACTUPC.cbl:3871` | Customer aggregate root / customer-service |
| Account | Account | Credit card account with an 11-digit ACCT-ID; carries the credit limit, used credit, current cycle accumulators, and disclosure group | `CVACT01Y.cpy:4-16`, `ACCTFILE.jcl:36-42` | CreditAccount aggregate root / account-service |
| Card | Card | 16-digit card number, CVV, embossed name, expiry date, status; a card always belongs to exactly one account | `CVACT02Y.cpy:4-10`, `COCRDLIC.cbl:213-217` | Card entity / card-service |
| Card Xref | Card-account cross-reference | The unique mapping card number→(customer ID, account ID); the primary key is the card number, plus an alternate index by account number | `CVACT03Y.cpy:4-7`, `XREFFILE.jcl:72-77`, `CARDDEMO.CSD:63-64` | CardAccountLink (two unique indexes: card_no / acct_id) |
| Transaction | Transaction | A **posted** accounting detail, 16-character TRAN-ID primary key, 350 bytes, including merchant data and two timestamps | `CVTRA05Y.cpy:4-17`, `CBTRN02C.cbl:424-442` | LedgerEntry / ledger-service |
| Daily Transaction | Daily transaction | The **not yet posted** external flow (QSAM sequential file, no key, replayable); after per-record validation it becomes a Transaction or goes into the reject file | `CVTRA06Y.cpy:4-17`, `POSTTRAN.jcl:23-38` | TransactionInflow (ingestion staging table) / ingestion-service |
| Transaction Category Balance | Transaction category balance | The **accumulated amount** for account × transaction type × transaction category, used as the interest calculation base; unrelated to the account balance | `CVTRA01Y.cpy:4-9`, `CBACT04C.cbl:462-466` | CategoryAccumulator (dimensional aggregate) / ledger-service |
| Disclosure Group | Disclosure group | Pricing table that determines the annual rate by (group identifier, type, category); three group values `A`, `DEFAULT`, `ZEROAPR` | `CVTRA02Y.cpy:4-9`, `discgrp.txt:1-51`, `CBACT04C.cbl:415-460` | InterestRatePlan / pricing-service |
| Transaction Type | Transaction type | Two-character business action classification `01`-`07` (Purchase…Adjustment) | `CVTRA03Y.cpy:5-6`, `trantype.txt:1-7`, `TRNTYPE.ddl:1-4` | Enum TransactionType |
| Transaction Category | Transaction category | 4-character sub-category under a type `0001`-`0005`, determining interest and accounting semantics | `CVTRA04Y.cpy:6-8`, `trancatg.txt:1-18`, `TRNTYCAT.ddl:1-7` | Enum TransactionCategory |
| Authorization | Authorization | Real-time limit decision for a card transaction, returning a response code/reason code; the result is written to the IMS pending authorization (DB2 is written only when a person flags fraud) | `COPAUA0C.cbl:440-470`, `:660-716` | AuthorizationDecision / authorization-service |
| Pending Authorization | Pending authorization (pre-authorization) | An approved authorization not yet matched to a posting; it holds available credit; a two-level structure of summary segment + detail segment; expired ones are deleted by batch | `CIPAUSMY.cpy:19-31`, `CIPAUDTY.cpy:19-54`, `DBPAUTP0.dbd:28-37`, `CBPAUP0C.cbl:284-296` | PendingAuth aggregate + credit hold (Authorization Hold) |
| Authorization Response Code | Authorization response code | `00`=approved, `05`=declined; also determines the value of the approved amount | `COPAUA0C.cbl:685-695`, `AUTHFRDS.ddl:9` | Enum AuthDecision (APPROVED/DECLINED) |
| Decline Reason | Decline reason code | 4 characters: `0000` none, `3100` subject not found, `4100` insufficient limit, `4200` card not active, `4300` account closed, `5100` card fraud, `5200` merchant fraud, `9000` other | `COPAUA0C.cbl:698-716`, `AUTHFRDS.ddl:10` | Enum DeclineReason (separated from the response code) |
| Fraud Flag | Fraud flag | On the authorization detail, `F`=fraud confirmed, `R`=fraud removed; on confirmation it is persisted to DB2 and the report date is recorded | `CIPAUDTY.cpy:50-53`, `COPAUS2C.cbl:81-82,137`, `AUTHFRDS.ddl:24-25` | FraudCase / fraud-service |
| Credit Limit | Credit limit | Account-level total credit line; available credit = limit − used credit (computed at runtime, not persisted) | `CVACT01Y.cpy:8`, `COPAUA0C.cbl:666-676` | CreditAccount.creditLimit + computed property availableCredit |
| Cash Credit Limit | Cash advance limit | Separate ceiling for cash advances; the legacy system only stores and displays it, with **no validation logic at all** | `CVACT01Y.cpy:9`, `CIPAUSMY.cpy:24`, `COPAUA0C.cbl:810` | CashAdvanceLimit value object (validation rules to be added) |
| Current Cycle Credit/Debit | Current cycle credit/debit accumulators | Two 12-byte amount fields; per the code: **positive transactions accumulate into CREDIT and negative transactions into DEBIT** (the naming is the literal opposite of the direction) | `CVACT01Y.cpy:13-14`, `CBTRN02C.cbl:548-551` | BillingCycleAccumulator (with positive/negative direction semantics) |
| Bill Payment | Bill payment | Online full repayment: amount = current debt, writes a `02`/`0002` transaction, reduces the debt to 0; rejected when the debt is ≤0 | `COBIL00C.cbl:198-202`, `:212-236`, `trancatg.txt:7` | BillPayment / payment-service |
| Interest Accrual | Interest accrual | Monthly interest = category balance × disclosure group annual rate ÷ 1200; generates a `01`/`0005` "System" transaction; the fee paragraph is an empty implementation | `CBACT04C.cbl:464-465`, `:474-495`, `:518-520` | InterestAccrual / interest-service |
| Posting | Posting | Turns a validated daily transaction into a Transaction and updates the category balance and account balance (accumulating in the same positive/negative direction) | `CBTRN02C.cbl:424-445`, `:467-556`, `POSTTRAN.jcl:23` | PostingService (batch + real time, isomorphic) |
| Reject | Posting reject | On validation failure, writes the whole 350-byte daily transaction + 80-byte validation trailer to DALYREJS | `CBTRN02C.cbl:446-461`, `POSTTRAN.jcl:34-38` | PostingException queue + failure code enum |
| Statement / Report | Statement / report | Two kinds of output: ① account statement (plain text + HTML, data retrieved via a CALL subprogram); ② transaction detail report (133 columns, account subtotal/page total/grand total) | `CBSTM03A.CBL:9-10`, `:351`, `CBTRN03C.cbl:5`, `CVTRA07Y.cpy:4-66` | StatementService / reporting-service |
| User Type | User type | `A`=administrator (enters the CA00 administrator menu), `U`=regular user (enters the CM00 main menu) | `CSUSR01Y.cpy:22`, `COCOM01Y.cpy:27-28`, `COSGN00C.cbl:230-238` | IAM roles ADMIN / USER |
| Sign-on | Sign-on | Enter an 8-character user ID and password, compared against USRSEC in plain text; on success XCTL to the matching menu by user type | `COSGN00C.cbl:132-137`, `:209-239` | Identity Provider / SSO session |
| Branch Migration | Branch migration | Export the 5 master file types into a 500-byte multi-record file (five record types C/A/X/T/D), then split, validate, and rebuild normalized files on the target side | `CBEXPORT.cbl:5-8`, `CBIMPORT.cbl:5-10`, `CVEXPORT.cpy:3-19` | MigrationService (export/import + validation report) |
| Anti-Corruption Layer | Anti-corruption layer | Translation layer between the new system and legacy semantics: fixed-length copybook parsing, EBCDIC overpunch decoding, code value enum mapping, amount/identifier normalization | No such concept in the legacy system; equivalent duties are carried by `CBIMPORT` (splitting + validation) and `CBEXPORT` (assembly): `CBIMPORT.cbl:272-330` | acl-adapter module (standalone, testable) |
| Merchant | Merchant | The counterparty of a transaction: merchant number, name, city, ZIP code, MCC | `CVTRA05Y.cpy:11-14`, `AUTHFRDS.ddl:14-21` | Merchant value object |
| Available Credit | Available credit | Limit − used credit; when a pending authorization exists, the IMS summary segment limit − authorization balance is used instead | `COPAUA0C.cbl:666-676` | Computed property AvailableCredit (order preserved inside the aggregate) |
| Match Status | Match status | Match status between pending authorizations and posted flow: `P` pending, `D` declined at authorization, `E` pending expired, `M` matched to posting | `CIPAUDTY.cpy:45-49`, `AUTHFRDS.ddl:23` | Enum PendingAuthStatus |
| Cycle / Billing Cycle | Billing cycle | Expressed implicitly by the account expiry date and the current cycle accumulator fields; the legacy system has no separate cycle entity | `CVACT01Y.cpy:11-14` | BillingCycle value object (explicit modelling recommended) |

## 2. Term conflicts and disambiguation

| Term / field | Common misreading | True legacy semantics | Evidence |
|---|---|---|---|
| `ACCT-CURR-BAL` | "current available balance" | It is the **used credit (amount owed)**: at authorization `available credit = ACCT-CREDIT-LIMIT − ACCT-CURR-BAL`; at repayment `ACCT-CURR-BAL = ACCT-CURR-BAL − TRAN-AMT` | `COPAUA0C.cbl:674-675`, `COBIL00C.cbl:234` |
| `TRAN-CAT-BAL` (TranCatBal) | "account balance" | It is the **accumulated amount by account + type + category**, used only as the interest calculation base; unrelated to the account debt or limit | `CVTRA01Y.cpy:9`, `CBACT04C.cbl:464-465` |
| The sign of the BILL PAYMENT amount | "the repayment amount should be negative" | The transaction amount is **positive** (= the debt before repayment), yet the online program subtracts it directly; if the same flow went through batch posting (`ADD DALYTRAN-AMT TO ACCT-CURR-BAL`) it would increase the debt in the opposite direction | `COBIL00C.cbl:224`, `:234`, `CBTRN02C.cbl:547` |
| `CUST-ADDR-LINE-3` | "the customer has no city field, the city is missing" | The city **is stored in address line 3** and is displayed and written back on screen as the `ACSCITY` field | `COACTUPC.cbl:1333`, `:2935`, `COACTVWC.cbl:513` |
| `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | "CREDIT=repayment, DEBIT=purchase" | The code splits by the sign of the amount: **positive (purchase) goes into CREDIT, negative (refund/reversal) goes into DEBIT**, which is the opposite of the literal "credit/debit"; moreover the over-limit check uses `CREDIT − DEBIT + this amount`, so when a refund exists the refund direction is counted twice | `CBTRN02C.cbl:548-551`, `:403-413` |
| The `04-xxxx` categories of Authorization vs Transaction | "an authorization is also a transaction/accounting entry" | An authorization only writes the IMS pending authorization and **does not produce a TRANSACT record** (COPAUA0C has no TRANSACT file access; its data sets are only ACCTDAT/CUSTDAT/CCXREF) | `COPAUA0C.cbl:35-39`, `:472-479` |
| Daily Transaction vs Transaction | The two are used interchangeably as "transaction" | The former is the flow awaiting posting (no key, may be delivered repeatedly); the latter is the posted detail (unique key, no idempotency protection) | `CVTRA06Y.cpy:4`, `CVTRA05Y.cpy:4`, `CBTRN02C.cbl:547-553` |
| The `R` of Fraud Flag | "R = Reject (declined)" | `R` = **Fraud Removed**; it is completely unrelated to authorization decline code `05` and posting reject codes 100/101/102/103/109 | `CIPAUDTY.cpy:51-52`, `COPAUS2C.cbl:81-82` |
| Decline (authorization decline) vs Reject (posting reject) | Both are called "decline" | Decline happens at authorization: response code `05` + a 4-character reason code; Reject happens at posting: a 4-character validation code written to DALYREJS. The two code tables are not interchangeable | `COPAUA0C.cbl:688`, `CBTRN02C.cbl:385-419` |
| "expired authorizations release credit" | Believing that deleting an expired authorization automatically restores available credit | When `CBPAUP0C` deletes a summary/detail it only reduces `PA-APPROVED-AUTH-CNT/AMT` and `PA-DECLINED-AUTH-CNT/AMT` and **never reduces `PA-CREDIT-BALANCE`**, while available credit is computed as `limit − PA-CREDIT-BALANCE` → expired authorizations hold credit forever (inconsistent with the extension README) | `CBPAUP0C.cbl:284-296`, `COPAUA0C.cbl:666-667`, `:817` |
| `0000` decline reason | Believing it stands for "declined" | `0000` means **no decline reason** (i.e. the default value on approval); a decline always carries a non-zero 4-character code | `COPAUA0C.cbl:698-716` |
| The triple identity of transaction category `0002` | Believing category codes are globally unique | The same `0002` is "regular cash advance" under type `01`, "electronic payment" under `02`, and "online purchase authorization" under `04` — **it must be read together with the type code** | `trancatg.txt:2,7,13` |

## 3. Concepts with no legacy counterpart

| Concept | Description | Evidence |
|---|---|---|
| Anti-Corruption Layer | The legacy system has no explicit translation layer; semantic conversion is scattered across `CBIMPORT` (read a 500-byte export record → MOVE it field by field into the target copybook) and the online programs (`NUMVAL`/`UPPER-CASE` conversion in place) | `CBIMPORT.cbl:288-300`, `COTRN02C.cbl:204`, `COSGN00C.cbl:132` |
| Idempotency key / event ID | Daily transactions have no business unique number and posting relies on file order; repeated delivery would post twice | `CBTRN02C.cbl:547-553` |
| Billing cycle entity | No cycle master data; cycle semantics are implied by `ACCT-CURR-CYC-*` and the expiry date | `CVACT01Y.cpy:11-14` |
| Audit log | No separate audit table/file; only the authorization extension defines an error log record layout `CCPAUERY` (used by COPAUA0C after `COPY` for CICS/IMS/MQ error records) | `CCPAUERY.cpy:19-30`, `COPAUA0C.cbl:185` |

## 4. Naming and prefix quick reference

| Prefix / suffix | Meaning | Evidence |
|---|---|---|
| `CB*` programs | Batch, e.g. CBTRN02C, CBACT04C, CBEXPORT | `CBTRN02C.cbl:4`, `CBACT04C.cbl:4` |
| `CO*` programs | Online (CICS), e.g. COSGN00C, COTRN02C, COPAUA0C | `COSGN00C.cbl:4`, `COTRN02C.cbl:4` |
| Trailing letter `C` in program names | 33 of the 44 programs end with `C` (COBOL member convention); the 11 that do not: COBTUPDT, COACCT01, CODATE01, COBSWAIT, CBEXPORT, CBIMPORT, CBSTM03A, CBSTM03B, PAUDBLOD, PAUDBUNL, DBUNLDGS | The 44 program file names |
| `CV*` copybooks | Legacy VSAM record layouts (CVACT/CVCUS/CVTRA/CVEXPORT) | `CVACT01Y.cpy:4`, `CVTRA01Y.cpy:4` |
| `CS*` copybooks | Common/system services (COMMAREA, date, messages, user, DB2 common segments) | `COCOM01Y.cpy:19`, `CSDAT01Y.cpy:2`, `CSUSR01Y.cpy:17` |
| `CI*` copybooks | IMS segment layouts (CIPAUSMY/CIPAUDTY) | `CIPAUSMY.cpy:19`, `CIPAUDTY.cpy:19` |
| `CC*` copybooks | Authorization messages and work areas (CCPAURQY/CCPAURLY/CCPAUERY, the CC-WORK-AREAS of CVCRD01Y) | `CCPAURQY.cpy:19`, `CVCRD01Y.cpy:1` |
| CICS transaction code families | `CA` account, `CC` card/sign-on, `CT` transaction, `CU` user, `CM` main menu, `CR` report, `CB` bill payment, `CP` authorization, `CD*` MQ query (CDRA/CDRD), `CDV` development use | `CARDDEMO.CSD:306-488`, `CRDDEMO2.csd:39-69`, `CRDDEMOM.csd:17-28` |
| `-TXN`/`-TRN` abbreviations | Both `TRAN` and `TRN` exist in the legacy code (e.g. TRNNAMEO, TRAN-ID) and refer to the same "transaction" concept | `COBIL00C.cbl:325`, `CVTRA05Y.cpy:5` |

## 5. Unresolved items

| Item | Status | Evidence |
|---|---|---|
| Credit restoration rule after a pending authorization expires | The code does not implement the `PA-CREDIT-BALANCE` rollback; the business expectation (see the extension README) is inconsistent with the implementation and awaits business confirmation | `CBPAUP0C.cbl:284-296` |
| Semantics of `PA-AUTH-STATUS` | The field is defined but never assigned, so its difference from `PA-MATCH-STATUS` cannot be confirmed | `CIPAUSMY.cpy:21` |
| Boundary between "interest accrual" and "fees" | `1400-COMPUTE-FEES` is an empty paragraph and the fee rules are unconfirmed | `CBACT04C.cbl:518-520` |
