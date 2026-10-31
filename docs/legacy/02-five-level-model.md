# CardDemo Legacy System Five-Level Business Model (As-Is)

> Method: built bottom-up (L5→L1), with evidence (program / paragraph / line number) annotated on every fact.
> Evidence baseline: the `carddemo-cobol` working copy (44 COBOL programs, 62 copybooks, 12 VSAM files,
> 3 DB2 tables, 2 IMS segments, 40 JCL). Items that cannot be confirmed from the code are listed in §6 "Unresolved items" rather than inferred.
> Depth tier: L3 implementation level (basis: involves customer funds, authorization and accounting, legacy code migration, and business continuity).

---

## 1. L1 Business model

### 1.1 System positioning

CardDemo is a demonstration **credit card issuer back-office core system** that covers the complete loop from customer/account/card record creation to transaction intake, authorization decisions,
end-of-day posting, revolving interest, bill payment, reporting, and user permission management.
Evidence: the `app/cbl` online program family, the `app/jcl` batch jobs, and the `app/app-authorization-ims-db2-mq` authorization sub-application.

### 1.2 Business capability list

| ID | Business capability | Implementation | Evidence |
|---|---|---|---|
| C1 | Identity authentication and menu authorization | COSGN00C, COADM01C, COMEN01C | `COSGN00C.cbl:209-257`; `COADM02Y.cpy:19-59`; `COMEN02Y.cpy:19-98` |
| C2 | User security management (create/read/update/delete) | COUSR00C/01C/02C/03C | `COUSR00C.cbl:586-629`; `COUSR01C.cbl:238-266`; `COUSR02C.cbl:111-126`; `COUSR03C.cbl:174-192` |
| C3 | Customer information management | CUSTDAT, CBCUS01C, COACTUPC | `CVCUS01Y.cpy:4-23`; `CBCUS01C.cbl` (read and print); `COACTUPC.cbl:1560-1662` |
| C4 | Credit card account management (limit/balance/dates/group) | ACCTDAT, COACTVWC, COACTUPC, CBACT01C/03C | `CVACT01Y.cpy:4-17`; `COACTVWC.cbl:471-525`; `COACTUPC.cbl:9699-WRITE-PROCESSING 3888-4104` |
| C5 | Card management (card number-account-customer mapping, card status, expiry) | CARDDAT, CCXREF, COCRDLIC/SLC/UPC, CBACT02C | `CVACT02Y.cpy:4-12`; `CVACT03Y.cpy:4-9`; `COCRDLIC.cbl:1123-1261`; `COCRDUPC.cbl:1420-1495` |
| C6 | Transaction intake and maintenance (entry/list/details) | TRANSACT, COTRN00C/01C/02C | `CVTRA05Y.cpy:4-17`; `COTRN02C.cbl:442-466`; `COTRN00C.cbl:591-654`; `COTRN01C.cbl:267-295` |
| C7 | Credit card authorization decision | COPAUA0C, IMS PAUTSUM0/PAUTDTL1, DB2 AUTHFRDS | `COPAUA0C.cbl:438-466,660-731`; `AUTHFRDS.ddl:1-28`; `DBPAUTP0.dbd` |
| C8 | End-of-day transaction posting (validation/bookkeeping/rejection) | CBTRN02C, DALYTRAN, TCATBALF, DALYREJS | `CBTRN02C.cbl:202-234,370-559` |
| C9 | Revolving interest calculation | CBACT04C, DISCGRP | `CBACT04C.cbl:180-232,462-500` |
| C10 | Bill payment | COBIL00C | `COBIL00C.cbl:197-244` |
| C11 | Transaction reports (online submission + batch printing) | CORPT00C, CBTRN03C, TDQ JOBS | `CORPT00C.cbl:212-256,498-535`; `CBTRN03C.cbl:173-203` |
| C12 | Branch migration data export/import | CBEXPORT, CBIMPORT, CVEXPORT | `CBEXPORT.cbl:151-157`; `CBIMPORT.cbl:272-285` |
| C13 | Expired authorization cleanup | CBPAUP0C (BMP) | `CBPAUP0C.cbl:156,280-346` |
| C14 | System integration (MQ query interface) | COACCT01, CODATE01 | `COACCT01.cbl:191-198,393-426`; `CODATE01.cbl:339-361` |
| C15 | Transaction type/category maintenance (DB2) | COTRTLIC, COTRTUPC, COBTUPDT | `COTRTLIC.cbl:000200-000700`; `TRNTYPE.ddl` |

### 1.3 Macro business processes

1. **Customer and account lifecycle**: customer record creation → account opening (limit, open/expiry/reissue dates, disclosure group) → card issuance (card number↔account↔customer)
   → account/card status maintenance (`Y/N`) → account closure/card replacement.
2. **Purchase transaction lifecycle**: authorization request (real time, holds the limit) → settlement/end-of-day posting (batch, writes TRANSACT, updates the balance and
   the transaction category accumulators) → revolving interest accrual → statement generation → bill payment (full repayment) → delinquency/over-limit rejection.
3. **End-of-day batch processing**: daily transaction file → validation → posting/rejection → interest accrual → transaction report → backup/export.
4. **Administrative maintenance**: users and permissions, transaction types/categories, account/card data, expired authorization cleanup.

### 1.4 System boundary

- **Inside this system**: customer/account/card/transaction/authorization master data and accounting processing, limit control, revolving interest, bill payment,
  user permissions, report data definitions, branch migration import/export.
- **Outside this system** (no implementation in the code; must not be invented in the target system): card scheme settlement network access, merchant acquiring,
  general ledger (GL) accounting and journal entries, credit approval and scoring models, collections, AML name screening, dispute/chargeback processes.
- Basis: none of the above external entities appear in the SQL/CICS statements or JCL of any of the 44 programs; `AUTHFRDS` has only fraud flag fields,
  with no name-screening logic (`AUTHFRDS.ddl:24`; the functional comment of `COPAUS2C.cbl` is Mark Authorization Message Fraud).

---

## 2. L2 Domain model

### 2.1 Aggregates and entities

| Entity | Primary key | Key attributes | Relationships | Evidence |
|---|---|---|---|---|
| Customer | CUST-ID 9(9) | Name, address, SSN, DOB, FICO, EFT, primary card flag | 1:N Card (via Xref) | `CVCUS01Y.cpy:4-23` |
| Account | ACCT-ID 9(11) | Status, current balance, credit limit, cash limit, open/expiry/reissue dates, current cycle credit/debit, ZIP, group ID | 1:N Card (via Xref), 1:N Transaction (via card number) | `CVACT01Y.cpy:4-17` |
| Card | CARD-NUM X(16) | Card account, CVV, embossed name, expiry date, status | N:1 Account | `CVACT02Y.cpy:4-12` |
| CardXref | CARD-NUM X(16) | Customer ID, account ID | Links Card↔Customer↔Account | `CVACT03Y.cpy:4-9` |
| Transaction | TRAN-ID X(16) | Type, category, source, amount, merchant five-tuple, card number, original/processing timestamps | N:1 Account (via card number) | `CVTRA05Y.cpy:4-17` |
| DailyTransaction | DALYTRAN-ID X(16) | Same as Transaction | Staged before posting | `CVTRA06Y.cpy:4-16` |
| TranCategoryBalance | (ACCT-ID, TYPE-CD, CAT-CD) | Accumulated amount | N:1 Account | `CVTRA01Y.cpy:4-10` |
| DisclosureGroup | (GROUP-ID, TYPE-CD, CAT-CD) | Annual interest rate | Determines interest | `CVTRA02Y.cpy:4-10` |
| TranType / TranCategory | TYPE-CD / (TYPE-CD,CAT-CD) | Description | Reference data | `CVTRA03Y.cpy`; `CVTRA04Y.cpy` |
| SecUser | SEC-USR-ID X(8) | Last name, first name, password, type | — | `CSUSR01Y.cpy:17-23` |
| PendingAuthSummary | PA-ACCT-ID | Credit limit, cash limit, credit balance, approved/declined counts and amounts | 1:N PendingAuthDetail | `CIPAUSMY` (IMS segment); `COPAUA0C.cbl:798-830` |
| PendingAuthDetail | (PA-ACCT-ID, PA-AUTH-DATE-9C, ...) | Authorization details, response code/reason, fraud flag | N:1 Summary | `CIPAUDTY`; `COPAUA0C.cbl:874` |
| AuthFraud | (CARD_NUM, AUTH_TS) | All authorization request/response fields, fraud flag | Standalone audit table | `AUTHFRDS.ddl:1-28` |

### 2.2 States and code enums (part of the ubiquitous language)

| Enum | Values | Semantics | Evidence |
|---|---|---|---|
| ACCT-ACTIVE-STATUS | `Y` / `N` | Account active/inactive | `COACTUPC.cbl:1856-1893` |
| CARD-ACTIVE-STATUS | `Y` / `N` | Card active/inactive | `COCRDUPC.cbl:845-873` |
| SEC-USR-TYPE | `A` / `U` | Administrator/regular user (only `A` reaches the administrator menu) | `COSGN00C.cbl:230-240` |
| Authorization response code | `00` approved / `05` declined | Also writes `AUTH_RESP_REASON` | `COPAUA0C.cbl:685-698` |
| Authorization decline reason | `3100` card/account/customer not found, `4100` insufficient limit, `4200` card not active, `4300` account closed, `5100` card fraud, `5200` merchant fraud, `9000` other | Implemented in the target system; only 3100/4100 are actually triggered in the legacy code | `COPAUA0C.cbl:699-718` (the 88 conditions for 4200/4300/5100/5200 are never SET) |
| Posting validation failure code | `100` invalid card number, `101` account not found, `102` over limit, `103` account expired, `109` account update failed | Written to DALYREJS | `CBTRN02C.cbl:385-420,556-558` |
| Transaction type | `01` purchase, `02` payment, `03` credit, `04` authorization, `05` refund, `06` reversal, `07` adjustment | Reference data | `app/data/ASCII/trantype.txt` |
| Transaction category | `010001` regular sales, `010002` cash advance, `010003` convenience check, `010004` ATM advance, `010005` interest | Reference data | `app/data/ASCII/trancatg.txt` |
| User type flag | `U` (menu item) / `A` (administrator only) | Menu visibility | `COMEN02Y.cpy:21-90` |

### 2.3 Key semantics (easily misunderstood, must be kept consistent in the target system)

1. `ACCT-CURR-BAL` is the **outstanding balance (amount owed)** (purchases increase it, payments decrease it), not the available credit (`CBTRN02C.cbl:547-552`).
2. Available credit = `ACCT-CREDIT-LIMIT − ACCT-CURR-BAL` (when there is no pending authorization summary)
   or `PA-CREDIT-LIMIT − PA-CREDIT-BALANCE` (when a pending authorization summary exists) (`COPAUA0C.cbl:665-683`).
3. `TRAN-CAT-BAL` is the **accumulated amount by type + category**, not the account balance (`CVTRA01Y.cpy:4-10`).
4. The bill payment transaction amount is positive, but it **reduces** the balance (`COBIL00C.cbl:224,234`).
5. The customer's `CITY` is actually stored in `CUST-ADDR-LINE-3` (`COACTVWC.cbl:471-525`; `COACTUPC.cbl:1322-1355`).
6. Interest = `TRAN-CAT-BAL × annual rate ÷ 1200`, summed per account and added to the balance **in a single posting**, and the current cycle credit/debit accumulators are then zeroed
   (`CBACT04C.cbl:462-468,350-354`).

---

## 3. L3 Process model

### P1 Sign-on and routing (COSGN00C)

| Step | Action | Rule | Evidence |
|---|---|---|---|
| 1 | Validate that user ID and password are non-empty | BR-SGN-01 | `COSGN00C.cbl:118-127` |
| 2 | Convert the input to uppercase and read USRSEC | BR-SGN-02 | `:132-136,211-219` |
| 3 | Plain-text equality comparison of the password | BR-SGN-03 (defect: newly added users are not uppercased) | `:223`; `COUSR01C.cbl:157` |
| 4 | `SEC-USR-TYPE='A'` → administrator menu, otherwise the regular menu | BR-SGN-04 | `:230-240` |

Exception branches: three kinds of messages — user not found / wrong password / file error (`:241-256`).

### P2 Account view and maintenance (COACTVWC / COACTUPC)

Flow: enter the account number → CXACAIX (account→customer) → ACCTDAT → CUSTDAT → display/edit → validate → `READ UPDATE`
both records → concurrency comparison → `REWRITE` the account → `REWRITE` the customer (if the customer write fails, `SYNCPOINT ROLLBACK`).
Evidence: `COACTUPC.cbl:2568-2580,9600-WRITE-PROCESSING 3888-4104,9700-CHECK-CHANGE-IN-REC 4109-4191`.
Decision points: status Y/N, 5 amounts, 3 date groups, DOB/SSN/FICO/name/address/phone/EFT/primary card flag (BR-ACTUP-*).
**Key conclusion**: this is a "transaction script" spanning the Account and Customer aggregates; in a DDD split it must be split into
two use cases in two bounded contexts and must not remain a single aggregate transaction.

### P3 Card list/details/maintenance (COCRDLIC / COCRDSLC / COCRDUPC)

- List: full-file key-order scan of CARDDAT, 7 lines per page, `PF7/PF8` paging forward/backward, row action `S` (view)/`U` (update),
  single-selection constraint (`COCRDLIC.cbl:1123-1261,1264-1380,1073-1121`).
- Details: read by card number primary key only, without validating that the card belongs to the account (`COCRDSLC.cbl:726-773`).
- Maintenance: card name (letters + spaces), status Y/N, expiry month 1-12, expiry year 1950-2099; a concurrency comparison is performed before saving with PF5
  (`COCRDUPC.cbl:806-944,1420-1495`). Defect: the CVV is overwritten with blanks during the rewrite (`:1464-1465`).

### P4 Transaction entry (COTRN02C)

Either the account number or the card number (one of the two) → cross-check against XREF → mandatory validation (type/category/source/description/amount/original date/processing date/
merchant ID/name/city/ZIP) → amount and date format validation → the transaction number takes "current maximum +1" → write TRANSACT.
Evidence: `COTRN02C.cbl:193-230,235-437,442-466`. Exceptions: card/account not found, duplicate transaction number, write failure.

### P5 End-of-day posting (CBTRN02C)

Per record: read DALYTRAN → validate the card number (100) → account exists (101) → over limit (102, `credit limit ≥ current cycle credit − debit + this transaction`)
→ account expiry date ≥ transaction original date (103) → if passed: update TCATBAL (create it when no record exists), update ACCOUNT
(balance, current cycle credit/debit), write TRANSACT; on failure write DALYREJS and count it; return code 4 when any rejection occurred.
Evidence: `CBTRN02C.cbl:202-232,370-422,424-444,467-559`.

### P6 Interest accrual (CBACT04C)

Traverse in TCATBAL key order (account+type+category) → settle the previous account when the account changes → look up the disclosure group rate (use the
`DEFAULT` group when missing) → when the rate is non-zero, accrue interest and write a transaction (type 01/category 0005, source System) → settle the account:
balance += accumulated interest, zero the current cycle credit/debit. Evidence: `CBACT04C.cbl:188-222,415-460,462-500,350-354`.
Note: `1400-COMPUTE-FEES` is an empty implementation (`:518-520`); fee calculation is not implemented.

### P7 Bill payment (COBIL00C)

Account number → read the account → reject when balance ≤ 0 ("You have nothing to pay") → user confirmation → take the card number for the account →
add a transaction (type 02/category 0002/source POS TERM/description BILL PAYMENT - ONLINE/amount=balance/merchant
999999999/BILL PAYMENT/N/A/N/A) → write TRANSACT → balance -= amount → update ACCOUNT.
Evidence: `COBIL00C.cbl:169-244`. **This process spans the "transaction" and "account" aggregates**, so the target system needs use-case orchestration.

### P8 Authorization decision (COPAUA0C)

Take the request from MQ (comma-separated: authorization date/time/card number/authorization type/card expiry/message type/source/…) → read XREF (3100)
→ read ACCOUNT/CUSTOMER (3100) → read the pending authorization summary → when a summary exists use `limit − credit balance`, otherwise use
`limit − current balance`; amount > available credit → decline 4100 → write back the response (`00` approved/`05` declined + reason code) →
MQ reply → update the summary (approved: count + amount + credit balance; declined: count + amount) → insert the authorization detail.
Evidence: `COPAUA0C.cbl:328-332,438-466,660-731,738-780,798-830`.
The target system must fill in the business intent behind the legacy dead code (4200/4300/5100/5200).

### P9 Authorization query and fraud flagging (COPAUS0C / COPAUS1C / COPAUS2C)

View the pending authorization summary and details by account; flag fraud on a single authorization. Evidence: the functional comments of the three programs (the `* Function` lines).

### P10 Reports (CORPT00C → CBTRN03C)

Select the report type online (monthly/annual/custom date range) → validate the date → confirm → generate JCL, write it to TDQ JOBS, and submit the batch job.
The batch job sorts by card number, filters by processing date over a closed interval, paginates every 20 lines, and subtotals at three levels (page/account/grand total).
Evidence: `CORPT00C.cbl:212-256,388-426,498-535`; `CBTRN03C.cbl:173-203,282-322`;
`proc/TRANREPT.prc:35-49`. Defect: the EOF branch counts the last transaction twice (`:197-203`).

### P11 User maintenance (COUSR00C-03C)

The list shows 10 lines per page (PF7/PF8), row action U/D → update/delete program; adding performs non-empty validation only; on update PF3 saves at the same time;
deletion has no "cannot delete yourself/the last administrator" protection. For evidence see §1.2 C2 and `COUSR00C.cbl:187-215`,
`COUSR02C.cbl:111-126`, `COUSR03C.cbl:174-192`.

### P12 Expired authorization cleanup (CBPAUP0C)

Parameters (expiry days, checkpoint interval, display interval, debug flag) → traverse PAUTSUM0/PAUTDTL1 → authorization age ≥ expiry days
→ delete the detail and roll back the summary counts → delete the summary when its count reaches 0. Evidence: `CBPAUP0C.cbl:156,196-209,280-346`.
Defects: the sample parameter `00` clears the current day's authorizations; the summary deletion condition checks the approved count twice; deleting details does not roll back the credit balance.

### P13 Branch migration export/import (CBEXPORT / CBIMPORT)

Export: write a 500-byte multi-record-type file in C→A→X→T→D order (with the fixed branch `0001`/region `NORTH`).
Import: dispatch by record type into 5 output files and write unknown types to the error file; there is no checksum or duplicate key validation.
Evidence: `CBEXPORT.cbl:151-157,269-551`; `CBIMPORT.cbl:272-285,449-478`.

### P14 System integration (COACCT01 / CODATE01)

MQ triggered (CKTI) → loop GET (5-second wait, SYNCPOINT per message) → `INQA` + 11-digit account number → read ACCTDAT
→ return labelled text; the request `MSGID/CORRELID` are echoed back unchanged. Evidence: `COACCT01.cbl:191-198,325-350,393-426`.

---

## 4. L4 System model

### 4.1 Programs and responsibilities (grouped)

| Group | Programs | Responsibility |
|---|---|---|
| Online-common | COSGN00C, COMEN01C, COADM01C, COBSWAIT, CSUTLDTC | Sign-on, menus, wait, date validation subprogram |
| Online-account | COACTVWC, COACTUPC | Account view, account+customer maintenance |
| Online-card | COCRDLIC, COCRDSLC, COCRDUPC | List pagination, details, maintenance |
| Online-transaction | COTRN00C, COTRN01C, COTRN02C, COBIL00C | List, details, entry, bill payment |
| Online-user | COUSR00C/01C/02C/03C | User create/read/update/delete |
| Online-report | CORPT00C | Report parameters and JCL submission |
| Batch-account | CBACT01C, CBACT02C, CBACT03C, CBACT04C, CBCUS01C | Account/card/cross-reference printing, interest |
| Batch-transaction | CBTRN01C, CBTRN02C, CBTRN03C | Daily transaction validation, posting, reports |
| Batch-migration | CBEXPORT, CBIMPORT | Branch migration export/import |
| Authorization sub-application | COPAUA0C, COPAUS0C, COPAUS1C, COPAUS2C, CBPAUP0C | Authorization decision, query, fraud flagging, cleanup |
| Transaction type sub-application | COTRTLIC, COTRTUPC, COBTUPDT | DB2 transaction type/category maintenance |
| MQ sub-application | COACCT01, CODATE01 | MQ account query, date query |

### 4.2 Call relationships

- Between online programs the calls are **all `XCTL`** (no `LINK`), passing context through a 2000-byte COMMAREA
  (the first 160 bytes of `COCOM01Y.cpy` + each program's private area).
- The only subprogram calls: `CALL 'CSUTLDTC'` (date validation, `COTRN02C.cbl:393-415`; the date paragraphs of `COACTUPC.cbl`).
- Abnormal termination: `CALL 'CEE3ABD'` (batch I/O failure).
- External calls: MQ (`MQOPEN/MQGET/MQPUT1/MQCLOSE`), IMS (`EXEC DLI`), DB2 (`EXEC SQL`).

### 4.3 Data storage

- **VSAM KSDS**: ACCTDAT, CARDDAT, CCXREF (+CXACAIX alternate index), CUSTDAT, TRANSACT, USRSEC,
  TCATBALF, DISCGRP, TRANTYPE, TRANCATG; sequential files DALYTRAN, DALYREJS.
- **DB2**: `CARDDEMO.TRANSACTION_TYPE`, `CARDDEMO.TRANSACTION_TYPE_CATEGORY`,
  `CARDDEMO.AUTHFRDS`.
- **IMS**: `PAUTSUM0` (root segment, by account) → `PAUTDTL1` (child segment, descending by `99999−YYDDD`).
- **TDQ**: `JOBS` (EXTRA/INREADER, 80 bytes fixed).

### 4.4 Interfaces

| Interface | Form | Contract points | Evidence |
|---|---|---|---|
| Terminal-online | BMS screens + AID keys | 21 mapsets; PF3/PF4/PF5/PF7/PF8/PF12 | `app/bms/*.bms`; `app/cpy-bms/*.CPY` |
| Program-program | COMMAREA 2000 bytes | CDEMO state machine `PGM-ENTER/REENTER` | `COCOM01Y.cpy` |
| Report submission | TDQ JOBS (80 bytes × 20 lines of JCL) | Includes SYMNAMES date parameters | `CORPT00C.cbl:81-127,498-535` |
| Authorization request/reply | IBM MQ character strings | 13+ comma-separated fields; 6-field reply | `COPAUA0C.cbl:354-370,722-731` |
| Account/date query | IBM MQ character strings | `INQA` + 11-digit account number; 5-second wait | `COACCT01.cbl:105-112,393-426` |
| File level | JCL DD / VSAM | Record lengths 50/60/150/300/350/500 | Header comments of each copybook |

---

## 5. L5 Implementation model

### 5.1 Runtime topology

- z/OS + CICS region (CARDDEMO group) + IMS DB/DC (DBPAUTP0, PSBPAUTB) + DB2 + IBM MQ + JES.
- Online transaction codes: CC00 (sign-on), CM00 (main menu), CA00 (administrator menu), CAVW (account view), CAUP (account update),
  CCLI/CCDL/CCUP (card list/details/update), CT00/CT01/CT02 (transaction list/details/entry), CB00 (bill payment),
  CU00-CU03 (users), CR00 (reports), COTR/COTU (transaction types), CDRA/CDRD (MQ), CP00 (authorization).
- Evidence: `app/csd/CARDDEMO.CSD`, `app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd`,
  `app/app-transaction-type-db2/csd/*.csd`, `app/app-vsam-mq/csd/CRDDEMOM.csd`.

### 5.2 Batch job orchestration

| Job | Main program | Input→output | Scheduling semantics |
|---|---|---|---|
| POSTTRAN | CBTRN02C | DALYTRAN→TRANSACT/TCATBALF/ACCTDAT/DALYREJS | End-of-day posting |
| INTCALC | CBACT04C (PARM=YYYYMMDDHH) | TCATBALF/DISCGRP/ACCTDAT→SYSTRAN | End-of-day/month-end interest accrual |
| TRANREPT | REPROC → SORT → CBTRN03C | TRANSACT→TRANREPT(133) | On-demand/end-of-day report |
| CBEXPORT / CBIMPORT | Same-named programs | 5 VSAM ↔ EXPFILE(500) | Branch migration |
| CBPAUP0J | CBPAUP0C (BMP) | IMS PAUTSUM0/PAUTDTL1 | End-of-day cleanup |
| ACCTFILE/CARDFILE/CUSTFILE/XREFFILE/TRANFILE/TCATBALF/TRANTYPE | CBACT01C/02C/03C/CBCUS01C | VSAM→print | Operations printing |
| DALYREJS | IDCAMS PRINT | DALYREJS→print | Rejection report |
| PRTCATBL | IDCAMS | TCATBALF→print | Reconciliation aid |

### 5.3 Runtime constraints (must be preserved or explicitly replaced during migration)

1. Key-ordered scan and pagination (7 cards per page, 10 transactions/users per page).
2. `READ UPDATE` locking + snapshot comparison to achieve optimistic concurrency (`COACTUPC.cbl:4109-4191`).
3. State machine COMMAREA (first entry/re-entry), replaced by stateless HTTP semantics in the web session.
4. Batch job failure means ABEND (return codes 16/999); after migration this must become batch results + rejection details.
5. MQ-triggered tasks consume in batches over a 5-second window.

---

## 6. Unresolved items tracking (L3 requirement)

| ID | Unresolved item | Impact | Owner | Expected resolution | Interim handling |
|---|---|---|---|---|---|
| U1 | Production transaction volume/peak TPS unknown (demo data only) | Affects capacity and partitioning design | Legacy system owner | Before target system load testing | Design for 100 TPS per instance and adjust after load testing |
| U2 | The historical production volume of `AUTHFRDS` and IMS pending authorization data is unknown | Affects the authorization service data migration and cleanup strategy | DBA | Before the migration plan review | Migrate only non-expired authorizations from the last 5 days |
| U3 | The card scheme authorization message specification (ISO8583 field mapping) is not reflected in the code | The external contract of the authorization service must be re-confirmed | Payment architect | Interface design phase | Implement the legacy comma-separated contract first and abstract it into DTOs |
| U4 | The data source for merchant fraud (5200) does not exist | That decline code cannot be triggered in production | Risk control | Requirement clarification | Keep the reason code; implement 5100 for now as a "card-level fraud flag" |
| U5 | Report format acceptance criteria (133-byte layout) | Affects regulatory/operations acceptance | Operations | Before UAT | The new system outputs JSON + CSV and the reporting platform owns the layout |
| U6 | Password policy and encryption requirements (legacy is plain text, 8 characters) | Security compliance | Security | Before go-live | PBKDF2 storage + minimum length validation, see ADR-0008 |

## 7. Legacy risk quantification (L3)

| Metric | Count | Evidence |
|---|---|---|
| COBOL programs | 44 | `glob **/*.cbl` |
| Copybooks | 62 (41 .cpy + 21 .CPY, of which 17 are BMS maps) | `app/cpy`, `app/cpy-bms` |
| DB2 tables | 3 | `ddl/*.ddl` |
| VSAM files | 12 (including 1 alternate index CXACAIX) | `app/csd/CARDDEMO.CSD` DEFINE FILE |
| IMS segments | 2 (PAUTSUM0, PAUTDTL1) | `ims/DBPAUTP0.dbd`, `PSBPAUTB.psb` |
| External interfaces | 6 categories (BMS terminal, COMMAREA, TDQ, MQ authorization, MQ query, files/JCL) | §4.4 |
| CICS transactions | About 20 | Each CSD |
| Batch jobs (JCL) | 40 files | `app/jcl` |

High-risk programs: `COPAUA0C` (authorization decision, directly declining/approving transactions), `CBTRN02C` (posting and limit validation),
`CBACT04C` (interest and balances), `COBIL00C` (fund deduction), `COACTUPC` (account/customer data and limits).

## 8. Migration impact assessment (L3)

| Item | Conclusion |
|---|---|
| Legacy programs requiring modification | 0 (greenfield build; the legacy system keeps running read-only in parallel) |
| Programs requiring rewrite | 38 (25 online + 13 batch/utility; the list is in `01-codebase-inventory.md` §8) |
| Programs that can be retired | 6 (COBSWAIT, CSUTLDTC, CBACT01C/02C/03C, CBCUS01C) |
| Data migration targets | 9 ASCII data files with 626 records in total (customer/account/card/cross-reference/daily transaction/transaction type/category/disclosure group/category balance); EBCDIC overpunch numerics must be decoded before import |
| Migration window | Legacy and new systems run in parallel for ≥ 2 accounting cycles; read-only query traffic is switched first and write operations last |

## 9. Business continuity (L3)

| Item | Plan |
|---|---|
| Downtime | Target 0 downtime: parallel run + read-only traffic switch; the write switch window is ≤ 30 minutes (after end-of-day) |
| Rollback plan | Keep the COBOL system and a read-only snapshot of the VSAM data; if the switch fails, roll back the CICS transaction routing and reverse the new system's data through end-of-day reconciliation |
| Contingency process | Authorization unavailable → degrade to the COBOL authorization sub-application; posting failure → keep the original DALYTRAN file and rerun it on the COBOL side |
| Core program BCP | `COPAUA0C` (authorization) and `CBTRN02C` (posting) must have an independent BCP and drill records |
