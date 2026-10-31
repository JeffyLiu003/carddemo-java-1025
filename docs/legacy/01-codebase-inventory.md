# 01 Legacy Code Technical Inventory (CardDemo COBOL)

**Evidence convention**: `file:line` refers to real source lines under `/Users/louis/work-space/Code-projects/iCode/carddemo-cobol/`; the "line count" values in the tables are all `wc -l` results. The scan baseline `/tmp/carddemo_scan.json` (scan_as400_codebase.py v1.1.0) reports 44 programs / 62 copybooks / 2 DB2 tables / 62 call edges; this document checks each of the 44 programs one by one, and the actual distribution is `app/cbl` 31, `app/app-authorization-ims-db2-mq/cbl` 8, `app/app-transaction-type-db2/cbl` 3, `app/app-vsam-mq/cbl` 2 (consistent with the scan baseline). The baseline's "2 DB2 tables" only counted tables directly referenced by SQL; the DDL actually contains 3 (see §4.2).

## 1. Codebase orientation

| Directory | Content | Evidence |
|---|---|---|
| app/cbl | 31 core COBOL programs (17 online CICS + 12 batch + 2 subprograms/utilities; including 2 with the uppercase `.CBL` extension) | `app/cbl` directory listing |
| app/app-authorization-ims-db2-mq | Authorization extension: cbl 8, cpy 9, cpy-bms 2, bms 2, csd 1, dcl 1, ddl 2, ims 6, jcl 5, data/EBCDIC 1 | `.../README.md`; `app/**` listing |
| app/app-transaction-type-db2 | Transaction type DB2 extension: cbl 3, cpy 2, cpy-bms 2, bms 2, csd 1, ctl 8, dcl 2, ddl 4, jcl 3 | `.../README.md` |
| app/app-vsam-mq | MQ query extension: cbl 2, csd 1 | `app/app-vsam-mq/README.md` |
| app/cpy | 30 business/utility copybooks (`*.cpy` + `COSTM01.CPY`) | Directory listing |
| app/cpy-bms | 17 BMS screen map copybooks (`*.CPY`, generated from `app/bms`) | Directory listing |
| app/bms | 17 online screen BMS source definitions | `app/bms/*.bms` |
| app/csd | `CARDDEMO.CSD`: 8 FILE, 17 MAPSET, 18 PROGRAM, 18 TRANSACTION, 1 TDQ | `CARDDEMO.CSD:1-505` |
| app/ctl | `REPROCT.ctl` (IDCAMS REPRO control cards) | Directory listing |
| app/data | `ASCII/` 9 text data sets, `EBCDIC/` 13 `.PS` data sets | `wc -l app/data/ASCII/*.txt` |
| app/jcl | 38 batch jobs | `ls app/jcl` |
| app/proc | `REPROC.prc` (containing PROC REPROC), `TRANREPT.prc` (which also defines `REPROC PROC` in the same file) | `app/proc/TRANREPT.prc:1` |
| app/asm, app/maclib | Assembly subroutines `COBDATFT`/`MVSWAIT` and macros | `app/asm/COBDATFT.asm`, `app/maclib/ASMWAIT.mac` |
| app/catlg, app/scheduler | Catalog listing snapshot, CA7/Control-M scheduling definitions | `app/catlg/LISTCAT.txt`, `app/scheduler/*` |
| (app/ddl does not exist) | The DDL is split across `app-transaction-type-db2/ddl` (4) and `app-authorization-ims-db2-mq/ddl` (2) | 6 files in total under `app/**/*.ddl` |

## 2. Program inventory (44)

| Program | Type | Transaction code | Main data files/tables | Function (one line) | Lines |
|---|---|---|---|---|---|
| CBACT01C | Batch | — | ACCTDATA → OUTFILE/ARRYFILE/VBRCFILE | Reads the account master file and rewrites it into 3 output formats: fixed-length/array/variable-length | 430 |
| CBACT02C | Batch | — | CARDDATA | Reads and prints the card file | 178 |
| CBACT03C | Batch | — | CARDXREF | Reads and prints the card-account cross-reference file | 178 |
| CBACT04C | Batch | — | TCATBALF, CARDXREF(+AIX), ACCTDATA, DISCGRP, TRANSACT | Calculates interest monthly and writes 01-0005 transactions | 652 |
| CBCUS01C | Batch | — | CUSTDATA | Reads and prints the customer file | 178 |
| CBEXPORT | Batch (migration) | — | 5 master files → EXPORT.DATA | Branch migration export into a 500-byte multi-record file | 582 |
| CBIMPORT | Batch (migration) | — | EXPORT.DATA → 5 target files + error file | Splits the export file and validates it field by field | 487 |
| CBSTM03A | Batch | — | TRNXFL, CARDXREF, CUSTDATA, ACCTDATA → STMTFILE/HTMLFILE | Generates statements (text + HTML) | 924 |
| CBSTM03B | Batch subprogram | — | TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE | Statement data retrieval and line layout (called by CBSTM03A in 14 places) | 230 |
| CBTRN01C | Batch (read-only verification) | — | DALYTRAN, CUST/XREF/CARD/ACCT/TRANSACT | Verifies the related data of each daily transaction, with OPEN INPUT throughout | 494 |
| CBTRN02C | Batch | — | DALYTRAN, TRANSACT, CARDXREF, DALYREJS, ACCTDATA, TCATBALF | Daily transaction posting: validation→reject write→balance update | 731 |
| CBTRN03C | Batch | — | TRANSACT, CARDXREF, TRANTYPE, TRANCATG, DATEPARM → TRANREPT | Transaction detail report (133 columns, with account subtotals/page totals) | 649 |
| COACTUPC | CICS online | CAUP | ACCTDAT, CUSTDAT, CXACAIX | Account maintenance (customer data, limit, cycle accumulators) | 4236 |
| COACTVWC | CICS online | CAVW | ACCTDAT, CUSTDAT, CXACAIX | Account view | 941 |
| COADM01C | CICS online | CA00 | — | Administrator menu (6 items after the DB2 extension) | 288 |
| COBIL00C | CICS online | CB00 | ACCTDAT, CXACAIX, TRANSACT | Bill payment: pay the full amount and write a 02-0002 transaction | 572 |
| COBSWAIT | Utility (batch) | — | — | Waits for the number of centiseconds given in PARM (CALL MVSWAIT) | 41 |
| COCRDLIC | CICS online | CCLI | CARDDAT, CARDAIX | Card list (dual entry by account/card number, pagination) | 1459 |
| COCRDSLC | CICS online | CCDL | CARDDAT, CARDAIX | Card detail view | 887 |
| COCRDUPC | CICS online | CCUP | CARDDAT, CARDAIX | Card maintenance (name/expiry date/status) | 1560 |
| COMEN01C | CICS online | CM00 | — | Regular user main menu (11 items) | 308 |
| CORPT00C | CICS online | CR00 | TRANSACT, TDQ JOBS, CALL CSUTLDTC | Report request: validates the date and submits a batch job through the TDQ | 649 |
| COSGN00C | CICS online | CC00 | USRSEC | Sign-on: validates the password and routes to the menu by A/U | 260 |
| COTRN00C | CICS online | CT00 | TRANSACT | Paginated browsing of the transaction list | 699 |
| COTRN01C | CICS online | CT01 | TRANSACT | Single transaction details | 330 |
| COTRN02C | CICS online | CT02 | TRANSACT, CCXREF, CXACAIX | Adds a transaction (takes the current maximum ID + 1) | 783 |
| COUSR00C | CICS online | CU00 | USRSEC | User list | 695 |
| COUSR01C | CICS online | CU01 | USRSEC | Adds a user | 299 |
| COUSR02C | CICS online | CU02 | USRSEC | Modifies a user | 414 |
| COUSR03C | CICS online | CU03 | USRSEC | Deletes a user | 359 |
| CSUTLDTC | Utility (subprogram) | — | — | Date validation (CALL CEEDAYS), called by CORPT00C/COTRN02C | 157 |
| COPAUA0C | CICS online (MQ triggered) | CP00 | MQ PAUTH.REQUEST/REPLY, CCXREF, ACCTDAT, CUSTDAT, IMS | Authorization request: read MQ→validate→decide→reply MQ→write IMS | 1026 |
| COPAUS0C | CICS online (BMS+IMS) | CPVS | CARDDAT, CARDAIX, ACCTDAT, CUSTDAT, IMS | Pending authorization summary browsing | 1032 |
| COPAUS1C | CICS online (BMS+IMS) | CPVD | IMS PAUTSUM0/PAUTDTL1 | Authorization detail view + fraud flag (LINK COPAUS2C to persist in DB2 and write back to IMS) | 604 |
| COPAUS2C | CICS online (IMS+DB2) | Called by CPVD | IMS, DB2 AUTHFRDS | Marks/removes fraud and persists it to DB2 | 244 |
| CBPAUP0C | BMP (batch IMS) | — | IMS PAUTSUM0/PAUTDTL1 | Deletes expired pending authorizations and rolls back the accumulators | 386 |
| DBUNLDGS | Batch (IMS unload) | — | IMS DBPAUTP0 → GSAM PASFLDBD/PADFLDBD | Unloads authorization summaries/details from IMS into 2 GSAM files | 366 |
| PAUDBUNL | Batch (IMS unload) | — | IMS PAUTB → OUTFIL1/OUTFIL2 | Exports summaries/details by segment into 2 sequential files | 317 |
| PAUDBLOD | Batch (IMS load) | — | INFILE1/INFILE2 → IMS PAUTB | Loads authorization segments from 2 sequential files | 369 |
| COTRTLIC | CICS online (DB2) | CTLI | DB2 TRANSACTION_TYPE | Transaction type list (forward/backward cursor), update, delete | 2098 |
| COTRTUPC | CICS online (DB2) | CTTU | DB2 TRANSACTION_TYPE | Transaction type add/update | 1702 |
| COBTUPDT | Batch (DB2) | — | DB2 TRANSACTION_TYPE | Adds/updates/deletes transaction types from an input file | 237 |
| COACCT01 | CICS online (MQ) | CDRA | MQ, ACCTDAT | Consumes MQ requests and returns account details (INQA) | 620 |
| CODATE01 | CICS online (MQ) | CDRD | MQ | Consumes MQ requests and returns the system date and time | 524 |

**Verification conclusion**: `CARDDEMO.CSD` additionally contains `DEFINE PROGRAM(COCRDSEC)` and `DEFINE TRANSACTION(CDV1)` (`CARDDEMO.CSD:211-218`, `:388-398`), but the repository contains **no COCRDSEC source** (`glob app/**/COCRDSEC*` returns nothing); the CSD defines 26 PROGRAMs and 25 TRANSACTIONs in total.

## 3. Copybook inventory (62 = 41 `.cpy` + 21 `.CPY`)

Reference counts are obtained by grepping `COPY <name>` across the 44 programs (multiple COPYs in the same program count as 1).

| File name | Purpose/record length | Main field groups | Referencing programs (programs) |
|---|---|---|---|
| COCOM01Y.cpy | CICS COMMAREA, 160 bytes | CDEMO-GENERAL/CUSTOMER/ACCOUNT/CARD/MORE-INFO | 21 |
| COTTL01Y.cpy | Screen titles | CCDA-TITLE01/02, CCDA-THANK-YOU (40 each) | 21 |
| CSDAT01Y.cpy | Date/time work area | WS-CURDATE-DATA, WS-CURTIME | 21 |
| CSMSG01Y.cpy | Common messages (2×50) | CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY | 21 |
| CSUSR01Y.cpy | User security record, 80 bytes (VSAM `RECORDSIZE(80,80)`) | SEC-USR-ID/FNAME/LNAME/PWD/TYPE/FILLER | 14 |
| CVTRA05Y.cpy | Transaction master record, RECLN 350 | 13 business fields (ID/type/category/source/description/amount/4 merchant fields/card number/2 timestamps) | 11 |
| CVACT01Y.cpy | Account record, RECLN 300 | ACCT-ID, status, balance, dual limits, 3 dates, cycle accumulators, ZIP, GROUP | 16 |
| CVACT03Y.cpy | Card-account cross-reference, RECLN 50 | XREF-CARD-NUM/CUST-ID/ACCT-ID + 14 FILLER | 16 |
| CVACT02Y.cpy | Card record, RECLN 150 | CARD-NUM/ACCT-ID/CVV/embossed name/expiry/status | 10 |
| CVCUS01Y.cpy | Customer record, RECLN 500 | 3 name parts, 3 address lines, state/country/ZIP, 2 phones, SSN, government ID, birth date, EFT, primary card flag, FICO | 10 |
| CVCRD01Y.cpy | Card screen work area + AID constants | CC-WORK-AREAS, CCARD-AID, CCARD-NEXT-PROG | 7 |
| CSMSG02Y.cpy | Exception handling work area (formerly CABENDD.CPY) | ABEND-CODE, ABEND-CULPRIT, ABEND-DATA | 8 |
| CVTRA01Y.cpy | Transaction category balance, RECLN 50 | key (account 11 + type 2 + category 4), TRAN-CAT-BAL | 2 |
| CVTRA02Y.cpy | Disclosure group, RECLN 50 | key (group 10 + type 2 + category 4), DIS-INT-RATE | 1 |
| CVTRA03Y.cpy | Transaction type, RECLN 60 | TRAN-TYPE, TRAN-TYPE-DESC | 1 |
| CVTRA04Y.cpy | Transaction category, RECLN 60 | key (type 2 + category 4), TRAN-CAT-TYPE-DESC | 1 |
| CVTRA06Y.cpy | Daily transaction record, RECLN 350 | 13 DALYTRAN-* fields (same as CVTRA05Y) | 2 |
| CVTRA07Y.cpy | Transaction report layout | page header, detail lines, page/account/total lines (133 columns) | 1 |
| CVEXPORT.cpy | Migration export record, 500 bytes | REC-TYPE, TIMESTAMP, SEQ, BRANCH, REGION, 460-byte body with 5 REDEFINES | 2 |
| CUSTREC.cpy | 500-byte variant of the customer record | Same as CVCUS01Y, except that the birth date field is named CUST-DOB-YYYYMMDD | 1 |
| COSTM01.CPY | Statement transaction report layout | report line fields | 1 |
| COADM02Y.cpy | Administrator menu items (COUNT=6) | CDEMO-ADMIN-OPT-* | 1 |
| COMEN02Y.cpy | Main menu items (COUNT=11) | CDEMO-MENU-OPT-* | 1 |
| CODATECN.cpy | Date format conversion input/output record | CODATECN-TYPE/INP-DATE/OUT-DATE | 1 |
| CSLKPCDY.cpy | Code tables (phone area code, state, state+ZIP) | WS-US-PHONE-AREA-CODE-TO-EDIT and other tables | 1 |
| CSSETATY.cpy | Procedures for highlighting error fields | FLG-(TESTVAR1)-NOT-OK / (SCRNVAR2) | 2 |
| CSUTLDPY.cpy | Date validation procedures | EDIT-DATE-CCYYMMDD and other paragraphs | 1 |
| CSUTLDWY.cpy | Date validation work area | WS-EDIT-DATE-CCYYMMDD | 0 |
| CSSTRPFY.cpy | PF key→AID mapping procedures | YYYY-STORE-PFKEY | 0 |
| UNUSED1Y.cpy | Not referenced (version string `CardDemo_v1.0-56`) | — | 0 |
| COACTUP.CPY and 16 other basic BMS map copybooks | Online screen I/O field layouts (COACTUP, COACTVW, COADM01, COBIL00, COCRDLI, COCRDSL, COCRDUP, COMEN01, CORPT00, COSGN00, COTRN00, COTRN01, COTRN02, COUSR00, COUSR01, COUSR02, COUSR03) | `*I`/`*O` fields of each map | 1 each (2 for COCRDSL) |
| COPAU00.cpy | Authorization summary screen map | summary line I/O fields | 1 |
| COPAU01.cpy | Authorization detail screen map | detail I/O fields | 1 |
| COTRTLI.cpy | Transaction type list screen map | row array I/O fields | 1 |
| COTRTUP.cpy | Transaction type maintenance screen map | edit fields | 1 |
| CCPAURQY.cpy | Authorization request message, 152 bytes | 18 comma-separated fields (date…transaction ID) | 1 |
| CCPAURLY.cpy | Authorization reply message, 56 bytes | CARD-NUM/TRAN-ID/AUTH-ID/RESP/RESP-REASON/APPROVED-AMT | 1 |
| CCPAUERY.cpy | Authorization error log record | ERR-DATE/TIME/APPLICATION/PROGRAM/LOCATION/LEVEL… | 1 |
| CIPAUSMY.cpy | IMS root segment PAUTSUM0, 100 bytes | account/customer keys, limits, balances, approved/declined counts and amounts | 7 |
| CIPAUDTY.cpy | IMS child segment PAUTDTL1, 200 bytes | authorization key, card number, amount, merchant, response code, match/fraud status | 8 |
| IMSFUNCS.cpy | DL/I function code constants | FUNC-GU/GHU/GN/GHN/GNP/REPL/ISRT… | 3 |
| PAUTBPCB.CPY | Authorization database PCB mask | DBDNAME/segment level/status/PROCOPT/KEYFB | 3 |
| PADFLPCB.CPY | PADFLDBD (GSAM) PCB mask | As above (KEYFB 255) | 1 |
| PASFLPCB.CPY | PASFLDBD (GSAM) PCB mask | As above (KEYFB 100) | 1 |
| CSDB2RPY.cpy | Common DB2 procedures (including 9998-PRIMING-QUERY) | DB2 error handling paragraphs | 0 |
| CSDB2RWY.cpy | Common DB2 work area | WS-DB2-COMMON-VARS, SQLCA display fields | 0 |

## 4. Data store inventory

### 4.1 VSAM and sequential files (10 base clusters + 3 alternate index paths)

| Logical name | DSNAME | Key | Record length | Meaning | Evidence |
|---|---|---|---|---|---|
| ACCTDAT | ACCTDATA.VSAM.KSDS | KEYS(11 0) | 300/300 | Account master file | `ACCTFILE.jcl:36-44`, `CARDDEMO.CSD:1` |
| CARDDAT | CARDDATA.VSAM.KSDS | KEYS(16 0) | 150/150 | Card master file | `CARDFILE.jcl:50-58`, `CSD:25` |
| CARDAIX | CARDDATA.VSAM.AIX.PATH | KEYS(11 16) | 150 | Alternate index on the card's account number | `CARDFILE.jcl:83-102`, `CSD:13` |
| CCXREF | CARDXREF.VSAM.KSDS | KEYS(16 0) | 50/50 | Card→customer/account cross-reference | `XREFFILE.jcl:39-47`, `CSD:37-39` |
| CXACAIX | CARDXREF.VSAM.AIX.PATH | KEYS(11,25) | 50 | Alternate account-number index of CCXREF | `XREFFILE.jcl:72-92`, `CSD:63-64` |
| CUSTDAT | CUSTDATA.VSAM.KSDS | KEYS(9 0) | 500/500 | Customer master file | `CUSTFILE.jcl:46-54`, `CSD:50-52` |
| TRANSACT | TRANSACT.VSAM.KSDS | KEYS(16 0) | 350/350 | Transaction master file | `TRANFILE.jcl:49-57`, `CSD:76` |
| TRANSACT AIX | TRANSACT.VSAM.AIX.PATH | KEYS(26 304) | 350 | Alternate index on the transaction processing timestamp (defined for batch only) | `TRANIDX.jcl:25-44`, `TRANFILE.jcl:82-101` |
| USRSEC | USRSEC.VSAM.KSDS | KEYS(8,0) | 80/80 | User security file | `DUSRSECJ.jcl:64-73`, `CSD:88` |
| TCATBALF | TCATBALF.VSAM.KSDS | KEYS(17 0) | 50/50 | Transaction category balance (account + type + category) | `TCATBALF.jcl:36-44` |
| TRANTYPE | TRANTYPE.VSAM.KSDS | KEYS(2 0) | 60/60 | Transaction type reference data | `TRANTYPE.jcl:36-44` |
| TRANCATG | TRANCATG.VSAM.KSDS | KEYS(6 0) | 60/60 | Transaction category reference data | `TRANCATG.jcl:36-44` |
| DISCGRP | DISCGRP.VSAM.KSDS | KEYS(16 0) | 50/50 | Disclosure group (group + type + category → annual interest rate) | `DISCGRP.jcl:36-44` |

Sequential files: `DALYTRAN.PS` (daily transaction input, LRECL 350, `POSTTRAN.jcl:30-31`), `DALYREJS(+1)` (reject record GDG, LRECL 430 = 350 data + 80 validation trailer, `POSTTRAN.jcl:34-38`, `CBTRN02C.cbl:176-181`), `EXPORT.DATA` (migration export, KEYS(4 28), RECORDSIZE 500, `CBEXPORT.jcl:32-33`). **Note**: the export cluster `KEYS(4 28)` covers bytes 29–32, which is 1 byte off from `EXPORT-SEQUENCE-NUM` (bytes 28–31, `CVEXPORT.cpy:16`).

### 4.2 DB2 tables (3, all with a unique index)

| Table | Primary key/index | Key columns | Using programs | Evidence |
|---|---|---|---|---|
| CARDDEMO.TRANSACTION_TYPE | PK(TR_TYPE); XTRAN_TYPE | TR_TYPE CHAR(2), TR_DESCRIPTION VARCHAR(50) | COBTUPDT, COTRTLIC, COTRTUPC | `TRNTYPE.ddl:1-4`, `XTRNTYPE.ddl:1-4` |
| CARDDEMO.TRANSACTION_TYPE_CATEGORY | PK(TRC_TYPE_CODE,TRC_TYPE_CATEGORY); X_TRAN_TYPE_CATG; FK→TRANSACTION_TYPE ON DELETE RESTRICT | TRC_TYPE_CODE CHAR(2), TRC_TYPE_CATEGORY CHAR(4), TRC_CAT_DATA VARCHAR(50) | No program SQL reference (only extracted by `TRANEXTR.jcl:112`, loaded by `DB2LTCAT.ctl:15`, declared in `DCLTRCAT.dcl`) | `TRNTYCAT.ddl:1-7`, `XTRNTYCAT.ddl:1-5` |
| CARDDEMO.AUTHFRDS | PK(CARD_NUM,AUTH_TS); XAUTHFRD(CARD_NUM ASC, AUTH_TS DESC) | 26 columns, including AUTH_RESP_CODE, AUTH_RESP_REASON, AUTH_FRAUD, MATCH_STATUS, ACCT_ID, CUST_ID | COPAUS2C (`INSERT`/`UPDATE`) | `AUTHFRDS.ddl:1-28`, `XAUTHFRD.ddl:1-4`, `COPAUS2C.cbl:142`, `:223` |

### 4.3 IMS and TDQ

| Object | Structure | Key fields | Evidence |
|---|---|---|---|
| DBD DBPAUTP0 (HIDAM/VSAM) | Root segment PAUTSUM0 (100 bytes, key ACCNTID 6 bytes P), child segment PAUTDTL1 (200 bytes, key PAUT9CTS 8 bytes) | Account key + authorization details | `DBPAUTP0.dbd:28-37` |
| DBD DBPAUTX0 (INDEX) | Index segment PAUTINDX (6 bytes, FREQ=100000) | ACCNTID pointing to PAUTSUM0 | `DBPAUTX0.dbd:27-31` |
| PSB PSBPAUTB / PSBPAUTL | PAUTBPCB: PROCOPT=AP (BMP) / PROCOPT=L (load), two SENSEG levels | Dual PSB for online/BMP and loading | `PSBPAUTB.psb:17-20`, `PSBPAUTL.psb:17-20` |
| GSAM PADFLDBD / PASFLDBD | RECORD=(200) / (100), RECFM=F | Authorization input/output flat file databases | `PADFLDBD.DBD:22-27`, `PASFLDBD.DBD:22-27` |
| TDQ JOBS | TYPE(EXTRA), DDNAME(INREADER), RECORDSIZE(80) FIXED | CICS internal reader, submits batch jobs | `CARDDEMO.CSD:499-505` |

## 5. Batch job inventory (`app/jcl` 38 + authorization extension 5 + transaction type extension 3 = 46)

| JCL | Main program | Input DD → output DD | Purpose | Evidence |
|---|---|---|---|---|
| POSTTRAN.jcl | CBTRN02C | TRANFILE/DALYTRAN/XREFFILE/ACCTFILE/TCATBALF → DALYREJS | Daily transaction posting | `POSTTRAN.jcl:23-42` |
| INTCALC.jcl | CBACT04C (`PARM='2022071800'`) | TCATBALF/XREFFILE/XREFFIL1/ACCTFILE/DISCGRP → TRANSACT(SYSTRAN) | Interest calculation | `INTCALC.jcl:22-41` |
| TRANREPT.jcl | REPROC + SORT + CBTRN03C | TRANSACT → TRANSACT.BKUP → TRANSACT.DALY → TRANREPT(133) | Transaction report | `TRANREPT.jcl:23-80` |
| DALYREJS.jcl | IDCAMS | — → reject GDG base | Defines the DALYREJS GDG | `DALYREJS.jcl:19-31` |
| CBEXPORT.jcl | IDCAMS + CBEXPORT | 5 master files → EXPORT.DATA | Migration export | `CBEXPORT.jcl:24-63` |
| CBIMPORT.jcl | CBIMPORT | EXPORT.DATA → CUST/ACCT/XREF/TRNX/ERROR | Migration import | `CBIMPORT.jcl:22-60` |
| ACCTFILE.jcl / CARDFILE.jcl / CUSTFILE.jcl / XREFFILE.jcl / TRANFILE.jcl | IDCAMS (+SDSF switch file) | Defines clusters, AIX, PATH and REPROs the initial data | Cluster creation | `ACCTFILE.jcl:36-61`, `CARDFILE.jcl:50-102`, `CUSTFILE.jcl:46-71`, `XREFFILE.jcl:39-92`, `TRANFILE.jcl:49-101` |
| TCATBALF.jcl / DISCGRP.jcl | IDCAMS | Defines the KSDS and REPROs | Reference/balance file creation | `TCATBALF.jcl:36-61`, `DISCGRP.jcl:36-61` |
| TRANTYPE.jcl / TRANCATG.jcl | IDCAMS | Defines the KSDS and REPROs | Reference data creation | `TRANTYPE.jcl:36-61`, `TRANCATG.jcl:36-61` |
| TRANIDX.jcl | IDCAMS | Transaction AIX + PATH | Builds the missing transaction timestamp index | `TRANIDX.jcl:25-44` |
| PRTCATBL.jcl | REPROC + SORT | TCATBALF → TCATBALF.REPT(40) | Prints category balances (key ascending, output lines trimmed) | `PRTCATBL.jcl:29-58` |
| CREASTMT.JCL | SORT + IDCAMS + CBSTM03A | TRANSACT → TRXFL.SEQ → TRXFL.VSAM → STMTFILE/HTMLFILE(80) | Generates statements | `CREASTMT.JCL:44-79` |
| COMBTRAN.jcl | SORT + IDCAMS | TRANSACT.BKUP(0) + SYSTRAN(0) → COMBINED → transaction master file | Merges online/system transactions | `COMBTRAN.jcl:22-45` |
| TRANBKP.jcl | REPROC + IDCAMS | TRANSACT → BKUP, then deleted and recreated | Backs up and resets the transaction master file | `TRANBKP.jcl:23-51` |
| READACCT / READCARD / READCUST / READXREF | CBACT01C / CBACT02C / CBACT03C / CBCUS01C | Master files → PS or SYSOUT | File diagnostic printing | `READACCT.jcl:31-45`, `READCARD.jcl:22-29` |
| CBADMCDJ.jcl | DFHCSDUP | CSD source → CSD | Installs CICS resources | `CBADMCDJ.jcl:27` |
| CBPAUP0J.jcl (extension) | DFSRRC00 `PARM='BMP,CBPAUP0C,PSBPAUTB'` | IMS PSB/DBD libraries | Cleans up expired authorizations | `CBPAUP0J.jcl:24-47` |
| DBPAUTP0.jcl / UNLDPADB.JCL / LOADPADB.JCL / UNLDGSAM.JCL | DFSRRC00 (PARM `ULU,DFSURGU0` / `DLI,PAUDBUNL,PAUTBUNL` / `BMP,PAUDBLOD,PSBPAUTB` / `DLI,DBUNLDGS,DLIGSAMP` respectively) | IMS libraries ↔ sequential files | Authorization database unload/load maintenance | `DBPAUTP0.jcl:16`, `UNLDPADB.JCL:39`, `LOADPADB.JCL:27`, `UNLDGSAM.JCL:27` |
| CREADB21.jcl / TRANEXTR.jcl / MNTTRDB2.jcl (extension) | DSNTEP4 / DSNTIAUL / COBTUPDT | DB2 tables ↔ extract files | Table creation, extraction, batch maintenance | `app-transaction-type-db2/jcl/*` |

**Sorting/filtering steps of proc/TRANREPT.prc** (`TRANREPT.prc`): ① `STEP01R` invokes `REPROC` to unload `TRANSACT.VSAM.KSDS` into `TRANSACT.BKUP(+1)` (LRECL 350 FB, `:21-31`); ② `STEP05R` (SORT) declares `TRAN-CARD-NUM,263,16,ZD`, `TRAN-PROC-DT,305,10,CH`, `PARM-START-DATE,C'2022-01-01'`, `PARM-END-DATE,C'2022-07-06'` with SYMNAMES (`:38-42`), runs `SORT FIELDS=(TRAN-CARD-NUM,A)` and filters by processing date with `INCLUDE COND=(TRAN-PROC-DT,GE,PARM-START-DATE,AND,LE,PARM-END-DATE)` (`:44-46`), writing `TRANSACT.DALY(+1)`; ③ `STEP10R` runs CBTRN03C, reading DALY + CARDXREF + TRANTYPE + TRANCATG + DATEPARM and writing the 133-byte `TRANREPT` (`:57-78`). The date range is **hard-coded**, and TRANTREPT.jcl inlines the same control cards (`TRANREPT.jcl:40-50`).

## 6. Interface inventory

| Interface | Provider / consumer | Structure/format | Evidence |
|---|---|---|---|
| CICS COMMAREA | All 21 online programs XCTL each other | `CARDDEMO-COMMAREA` 160 bytes: FROM/TO TRANID+PROGRAM, USER-ID, USER-TYPE(A/U), PGM-CONTEXT(0/1), customer/account/card keys, previous/next mapset | `COCOM01Y.cpy:19-44` |
| TDQ JOBS | CORPT00C → JES internal reader | 80-byte fixed-length cards (`RECORDSIZE(80)`) | `CARDDEMO.CSD:499-505`, `CORPT00C.cbl:6`, `:462` |
| MQ authorization | External POS → COPAUA0C → external | Request 152 bytes, 18-field CSV; reply 56 bytes, 6-field CSV; queues `AWS.M2.CARDDEMO.PAUTH.REQUEST` / `.REPLY` | `CCPAURQY.cpy:19-36`, `CCPAURLY.cpy:19-24`, `README.md` (authorization extension) MQ configuration table |
| MQ account/date | COACCT01 / CODATE01 | Request carries `WS-FUNC='INQA'` + account key; reply text `SYSTEM DATE : …`; queues `CARD.DEMO.REPLY.ACCT`, `CARD.DEMO.ERROR` | `COACCT01.cbl:393-404`, `:198`, `:294`, `CODATE01.cbl:343-366` |
| IMS PSB/PCB | COPAUA0C/COPAUS0C/COPAUS1C/COPAUS2C/CBPAUP0C/PAUDB* | PSBPAUTB (AP/L), PSBPAUTL (load); segments PAUTSUM0/PAUTDTL1 | `PSBPAUTB.psb:17-20`, `PSBPAUTL.psb:17-20` |
| DB2 | COTRTLIC/COTRTUPC/COBTUPDT/COPAUS2C | Static SQL, 3 tables | `COTRTLIC.cbl:340-367`, `COPAUS2C.cbl:142` |
| JCL PARM | INTCALC → CBACT04C; CBPAUP0J → CBPAUP0C; WAITSTEP → COBSWAIT | 10-byte date `2022071800`; expiry days (default 5); centisecond wait | `CBACT04C.cbl:176-180`, `CBPAUP0C.cbl:196-199`, `COBSWAIT.cbl:37-38`, `WAITSTEP.jcl:20` |
| File level | See §4.1; JCL DD names correspond one-to-one with COBOL `ASSIGN TO` | ACCTFILE/CARDFILE/XREFFILE/TRANFILE/TCATBALF/DISCGRP/TRANTYPE/TRANCATG/DATEPARM/EXPFILE… | `CBTRN03C.cbl:29-57`, `CBEXPORT.cbl:35-65` |
| Inter-program CALL | CBSTM03A→CBSTM03B (14 places); CORPT00C/COTRN02C→CSUTLDTC; CBACT01C→COBDATFT (assembly); COBSWAIT→MVSWAIT (assembly) | COBOL CALL / assembly interface | `CBSTM03A.CBL:351`, `CORPT00C.cbl:392`, `COTRN02C.cbl:393`, `CBACT01C.cbl:231`, `COBSWAIT.cbl:38` |

## 7. Legacy risk quantification

| Metric | Count | Evidence |
|---|---|---|
| Programs | 44 (25 online / 17 batch / 2 utilities) | Count of `app/**/*.cbl` and `*.CBL`; §2 |
| Lines of code | 30,175 | Sum of `wc -l` over the 44 programs |
| Copybooks | 62 (41 `.cpy` + 21 `.CPY`, of which 21 are BMS maps) | `glob` count; §3 |
| DB2 tables / indexes | 3 / 3 | `ddl/*.ddl` (6 files) |
| VSAM file resources | 13 (10 KSDS base clusters + 3 AIX PATHs) | §4.1 |
| Sequential data sets | 3 kinds (daily transaction PS, reject GDG, export file) + a few temporary SYSTRAN/TRXFL/COMBINED | `POSTTRAN.jcl:30-38`, `CBEXPORT.jcl:32`, `COMBTRAN.jcl:26-33` |
| IMS segments | 3 (PAUTSUM0, PAUTDTL1, PAUTINDX) + 2 GSAM databases | §4.3 |
| CICS transactions / BC program definitions | 25 / 26 (including COCRDSEC/CDV1, which have no source) | Sum over the 4 CSDs; `CARDDEMO.CSD:211-218` |
| Batch jobs | 46 (38 + 5 + 3) | `ls app/jcl`, the jcl directories of the two extensions |
| External interface categories | 8 (COMMAREA, TDQ, MQ×2, IMS, DB2, JCL PARM, files) | §6 |
| Unresolved call edges | 6 (DISPLAY text misidentified by the scanner, not real calls) | `/tmp/carddemo_scan.json` → `unresolved_calls` |

**5 high-risk programs**

| Program | Risk reason | Evidence |
|---|---|---|
| CBTRN02C (731 lines) | All balance changes are concentrated here: `ADD DALYTRAN-AMT TO ACCT-CURR-BAL` followed by `REWRITE`, with no idempotency/rerun protection, so replaying the same DALYTRAN accrues twice; and there is no locking under concurrency | `CBTRN02C.cbl:547-553` |
| CBACT04C (652 lines) | The amount calculation uses `TRAN-CAT-BAL * DIS-INT-RATE / 1200` (monthly interest), and the transaction ID is built from the PARM date + sequence number; the fee paragraph `1400-COMPUTE-FEES` is an empty implementation | `CBACT04C.cbl:464-465`, `:476-480`, `:518-520` |
| COBIL00C (572 lines) | Online code directly rewrites the account balance (`COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT`), but the transaction amount written is **positive**, which is the opposite direction from the addition in batch posting; the full repayment decision relies only on `ACCT-CURR-BAL` | `COBIL00C.cbl:212-236`, `:224`, `:234` |
| COPAUA0C (1026 lines) | The three steps of authorization decision + MQ reply + IMS write have no distributed transaction; the four 88 conditions `CARD-NOT-ACTIVE`/`ACCOUNT-CLOSED`/`CARD-FRAUD`/`MERCHANT-FRAUD` are **never SET**, so the corresponding 4200/4300/5100/5200 branches are dead code | `COPAUA0C.cbl:141-145`, `:685-716` (only `INSUFFICIENT-FUND` is set true at `:670`, `:678`) |
| COACTUPC (4236 lines) | A single program handles maintenance of all account fields (limit, balance, cycle accumulators, customer address including CITY), so any field-level rule change lands in this file | `COACTUPC.cbl:1078-1140`, `:1333`, `:3704` |

## 8. Migration impact assessment

| Item | Value | Description and evidence |
|---|---|---|
| Legacy programs requiring modification | 0 | Greenfield build, the legacy source is not modified |
| Programs requiring rewrite | 38 | 25 online (CICS online rows in §2) + 13 batch (CBTRN02C, CBTRN03C, CBACT04C, CBSTM03A/B, CBEXPORT, CBIMPORT, CBPAUP0C, DBUNLDGS, PAUDBLOD, PAUDBUNL, COBTUPDT, CBTRN01C) |
| Programs that can be retired | 6 | COBSWAIT (wait utility), CSUTLDTC (date validation), CBACT01C/CBACT02C/CBACT03C/CBCUS01C (format demonstration and printing only; the ARRY/VBR output of `READACCT.jcl:31-45` has no downstream job) |
| Data migration targets | 9 ASCII data sets | Per-file record counts in the table below; there are also 13 `.PS` binary copies in `app/data/EBCDIC` |
| Total migrated records | 626 records / 161,549 bytes | `wc -l`: acctdata 50, carddata 50, cardxref 50, custdata 50, dailytran 300, discgrp 51, tcatbal 50, trancatg 18, trantype 7 |
| Reference data to rebuild | 3 kinds | 7 transaction types, 18 transaction categories (`trantype.txt`, `trancatg.txt`), 51 disclosure groups (`discgrp.txt`, including the three groups `A`/`DEFAULT`/`ZEROAPR`) |
| Runtime assets to retain | 25 CICS transaction codes, TDQ JOBS, 2 sets of MQ queues, 3 IMS segments | §4.3, §6 |
| Data quality gaps | 2 items | Each line of `cardxref.txt` is only 36 bytes (the 14-byte FILLER is missing, VSAM needs 50); the numeric fields of `acctdata.txt` are EBCDIC overpunch characters (see document 04 §5) |
| Open items | 3 items | COCRDSEC source is missing; `PA-AUTH-STATUS` has no assignment source; `TRANSACTION_TYPE_CATEGORY` is not read or written by any program SQL |
