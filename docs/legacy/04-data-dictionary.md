# 04 Data Dictionary (CardDemo Legacy System)

**Evidence convention**: `copybook:line` refers to real lines in `carddemo-cobol/app/cpy` (or the sub-application `cpy/`); `ddl:line` refers to `ddl/*.ddl`; `file:line` refers to `app/data/ASCII/*.txt`. **FILLER padding fields are not listed** (the trailing FILLER length of each entity is given in the last copybook line), so the field offsets can be verified by adding them up item by item.
**Length verification**: Customer 9+25+25+25+50×3+2+3+10+15+15+9+20+10+10+1+3+168 = 500 (`CVCUS01Y.cpy:5-23`); Account 11+1+12+12+12+10+10+10+12+12+10+10+178 = 300 (`CVACT01Y.cpy:5-17`); Transaction 16+2+4+10+100+11+9+50+50+10+16+26+26+20 = 350 (`CVTRA05Y.cpy:5-18`); Export 1+26+4+4+5+460 = 500 (`CVEXPORT.cpy:9-19`).
## 1. Entities, primary keys, and indexes

| Entity | Physical carrier / copybook | Primary key | Alternate index | Record length | Evidence |
|---|---|---|---|---|---|
| Customer | CUSTDATA.VSAM.KSDS / CVCUS01Y | CUST-ID | None | 500 | `CUSTFILE.jcl:46-52` |
| Account | ACCTDATA.VSAM.KSDS / CVACT01Y | ACCT-ID | None | 300 | `ACCTFILE.jcl:36-42` |
| Card | CARDDATA.VSAM.KSDS / CVACT02Y | CARD-NUM | **CARDAIX**: CARD-ACCT-ID, `KEYS(11 16)` | 150 | `CARDFILE.jcl:50-58`, `:83-88` |
| CardXref cross-reference | CARDXREF.VSAM.KSDS / CVACT03Y | XREF-CARD-NUM | **CXACAIX**: XREF-ACCT-ID, `KEYS(11,25)` — the account-number alternate index of CCXREF | 50 | `XREFFILE.jcl:39-47`, `:72-77`, `CARDDEMO.CSD:63-64` |
| Transaction master | TRANSACT.VSAM.KSDS / CVTRA05Y | TRAN-ID | TRANSACT AIX: TRAN-PROC-TS, `KEYS(26 304)` | 350 | `TRANFILE.jcl:49-57`, `:82-88` |
| DailyTransaction daily transaction | DALYTRAN.PS (QSAM) / CVTRA06Y | No key (sequential append) | None | 350 | `POSTTRAN.jcl:30-31` |
| TranCategoryBalance category balance | TCATBALF.VSAM.KSDS / CVTRA01Y | ACCT-ID+TYPE-CD+CAT-CD, `KEYS(17 0)` | None | 50 | `TCATBALF.jcl:36-44` |
| DisclosureGroup disclosure group | DISCGRP.VSAM.KSDS / CVTRA02Y | GROUP-ID+TYPE-CD+CAT-CD, `KEYS(16 0)` | None | 50 | `DISCGRP.jcl:36-44` |
| TranType transaction type | TRANTYPE.VSAM.KSDS / CVTRA03Y | TRAN-TYPE, `KEYS(2 0)` | None | 60 | `TRANTYPE.jcl:36-44` |
| TranCategory transaction category | TRANCATG.VSAM.KSDS / CVTRA04Y | TRAN-TYPE+TRAN-CAT, `KEYS(6 0)` | None | 60 | `TRANCATG.jcl:36-44` |
| SecUser user | USRSEC.VSAM.KSDS / CSUSR01Y | SEC-USR-ID, `KEYS(8,0)` | None | 80 | `DUSRSECJ.jcl:64-66` |
| AuthFraud authorization fraud | DB2 CARDDEMO.AUTHFRDS | (CARD_NUM, AUTH_TS) | XAUTHFRD (CARD_NUM ASC, AUTH_TS DESC, unique) | 26 columns | `AUTHFRDS.ddl:1-28`, `XAUTHFRD.ddl:1-4` |
| TransactionType (DB2) | DB2 CARDDEMO.TRANSACTION_TYPE | TR_TYPE | XTRAN_TYPE | 2 columns | `TRNTYPE.ddl:1-4`, `XTRNTYPE.ddl:1-4` |
| TransactionTypeCategory (DB2) | DB2 CARDDEMO.TRANSACTION_TYPE_CATEGORY | (TRC_TYPE_CODE, TRC_TYPE_CATEGORY) | X_TRAN_TYPE_CATG; FK→TRANSACTION_TYPE ON DELETE RESTRICT | 3 columns | `TRNTYCAT.ddl:1-7` |
| ExportRecord export record | EXPORT.DATA.VSAM / CVEXPORT | `KEYS(4 28)` (misaligned with SEQ by 1 byte, see 01 §4.1) | None | 500 | `CBEXPORT.jcl:32-33` |
| PendingAuthorization pending authorization (IMS) | DBPAUTP0: PAUTSUM0 root / PAUTDTL1 child | ACCNTID 6 bytes / PAUT9CTS 8 bytes | Index database DBPAUTX0 (PAUTINDX) | 100 / 200 | `DBPAUTP0.dbd:28-37`, `DBPAUTX0.dbd:27-31` |
## 2. Field-level definitions
### 2.1 Customer (CUSTDAT / CVCUS01Y, 500 bytes, PK CUST-ID) and Account (ACCTDAT / CVACT01Y, 300 bytes, PK ACCT-ID)

| Entity | Field name | Meaning | Type/Length | Value range/Enum | Source |
|---|---|---|---|---|---|
| Customer | CUST-ID | Customer number | 9(09) | 9-character fixed length with leading zeros (`000000001`) | `CVCUS01Y.cpy:5` |
| Customer | CUST-FIRST-NAME | First name | X(25) | Free text | `:6` |
| Customer | CUST-MIDDLE-NAME | Middle name | X(25) | Free text | `:7` |
| Customer | CUST-LAST-NAME | Last name | X(25) | Free text | `:8` |
| Customer | CUST-ADDR-LINE-1 | Address line 1 | X(50) | Street | `:9` |
| Customer | CUST-ADDR-LINE-2 | Address line 2 | X(50) | House number/unit | `:10` |
| Customer | CUST-ADDR-LINE-3 | Address line 3 | X(50) | **Actually holds the city (CITY)** | `:11`; `COACTUPC.cbl:1333`, `COACTVWC.cbl:513` |
| Customer | CUST-ADDR-STATE-CD | State/province code | X(02) | 2 letters (samples `NC`/`IN`) | `:12`; `custdata.txt:1` |
| Customer | CUST-ADDR-COUNTRY-CD | Country code | X(03) | 3 letters (sample `USA`) | `:13`; `custdata.txt:1` |
| Customer | CUST-ADDR-ZIP | ZIP code | X(10) | Contains trailing spaces (`12546     `) | `:14` |
| Customer | CUST-PHONE-NUM-1 | Phone 1 | X(15) | Contains parentheses/hyphens | `:15` |
| Customer | CUST-PHONE-NUM-2 | Phone 2 | X(15) | Same as above | `:16` |
| Customer | CUST-SSN | Social Security number | 9(09) | 9 digits, leading zeros | `:17` |
| Customer | CUST-GOVT-ISSUED-ID | Government-issued ID number | X(20) | Free text | `:18` |
| Customer | CUST-DOB-YYYY-MM-DD | Date of birth | X(10) | `YYYY-MM-DD` | `:19` |
| Customer | CUST-EFT-ACCOUNT-ID | EFT account number | X(10) | Free text | `:20` |
| Customer | CUST-PRI-CARD-HOLDER-IND | Primary cardholder indicator | X(01) | `Y` / `N` | `:21`; `COACTUPC.cbl:350` |
| Customer | CUST-FICO-CREDIT-SCORE | FICO credit score | 9(03) | 000-999 (samples 274/268) | `:22` |
| Account | ACCT-ID | Account number | 9(11) | 11 characters, leading zeros | `CVACT01Y.cpy:5` |
| Account | ACCT-ACTIVE-STATUS | Account status | X(01) | `Y` active / `N` closed | `:6`; `COACTUPC.cbl:193` |
| Account | ACCT-CURR-BAL | **Used credit (amount owed)**, not the available balance | S9(10)V99 | Amount; available credit = limit − this field | `:7`; `COPAUA0C.cbl:674-675` |
| Account | ACCT-CREDIT-LIMIT | Credit limit | S9(10)V99 | Amount | `:8` |
| Account | ACCT-CASH-CREDIT-LIMIT | Cash advance limit | S9(10)V99 | Amount | `:9` |
| Account | ACCT-OPEN-DATE | Open date | X(10) | `YYYY-MM-DD` | `:10` |
| Account | ACCT-EXPIRAION-DATE | Expiry date | X(10) | `YYYY-MM-DD` (field name spelled as in the source) | `:11` |
| Account | ACCT-REISSUE-DATE | Card reissue date | X(10) | `YYYY-MM-DD` | `:12` |
| Account | ACCT-CURR-CYC-CREDIT | Current cycle accumulated **positive amounts** (per the code: positive transactions = purchases accumulate here; the field name is the literal opposite of "credit") | S9(10)V99 | Amount | `:13`; `CBTRN02C.cbl:549` |
| Account | ACCT-CURR-CYC-DEBIT | Current cycle accumulated **negative amounts** (per the code: negative transactions = refunds/reversals accumulate here) | S9(10)V99 | Amount | `:14`; `CBTRN02C.cbl:551` |
| Account | ACCT-ADDR-ZIP | Billing ZIP code | X(10) | Text (sample `A000000000`) | `:15`; `acctdata.txt:1` |
| Account | ACCT-GROUP-ID | Disclosure group identifier | X(10) | Group code (empty in the sample; `A`/`DEFAULT`/`ZEROAPR` in DISCGRP) | `:16`; `discgrp.txt:1,18,35` |
### 2.2 Card (CARDDAT / CVACT02Y, 150 bytes, PK CARD-NUM, AIX=CARDAIX) and CardXref (CCXREF / CVACT03Y, 50 bytes, PK XREF-CARD-NUM, AIX=CXACAIX)

| Entity | Field name | Meaning | Type/Length | Value range/Enum | Source |
|---|---|---|---|---|---|
| Card | CARD-NUM | Card number | X(16) | 16 numeric characters (not numeric) | `CVACT02Y.cpy:5` |
| Card | CARD-ACCT-ID | Owning account number | 9(11) | Foreign key→ACCT-ID; CARDAIX key (offset 16) | `:6`; `CARDFILE.jcl:85` |
| Card | CARD-CVV-CD | CVV code | 9(03) | 000-999 | `:7` |
| Card | CARD-EMBOSSED-NAME | Embossed card name | X(50) | Free text | `:8` |
| Card | CARD-EXPIRAION-DATE | Card expiry date | X(10) | `YYYY-MM-DD` | `:9` |
| Card | CARD-ACTIVE-STATUS | Card status | X(01) | `Y` valid / `N` invalid | `:10`; `COCRDUPC.cbl:91` |
| Cross-reference | XREF-CARD-NUM | Card number (primary key) | X(16) | Same domain as CARD-NUM | `CVACT03Y.cpy:5` |
| Cross-reference | XREF-CUST-ID | Customer number | 9(09) | Foreign key→CUST-ID | `:6` |
| Cross-reference | XREF-ACCT-ID | Account number | 9(11) | Foreign key→ACCT-ID; CXACAIX key (offset 25) | `:7`; `XREFFILE.jcl:74` |
### 2.3 Transaction (TRANSACT / CVTRA05Y, 350 bytes, PK TRAN-ID) and DailyTransaction (DALYTRAN / CVTRA06Y, 350 bytes, no key)

| Entity | Field name | Meaning | Type/Length | Value range/Enum | Source |
|---|---|---|---|---|---|
| Transaction | TRAN-ID | Transaction ID | X(16) | Maximum key +1; system transaction = `PARM date` + sequence number | `CVTRA05Y.cpy:5`; `COTRN02C.cbl:444-451`, `CBACT04C.cbl:474-480` |
| Transaction | TRAN-TYPE-CD | Transaction type code | X(02) | `01`-`07` (§3.3) | `:6` |
| Transaction | TRAN-CAT-CD | Transaction category code | 9(04) | `0001`-`0005` (§3.4) | `:7` |
| Transaction | TRAN-SOURCE | Transaction source | X(10) | `POS TERM`/`System`/`OPERATOR` | `:8`; `dailytran.txt:1-2` |
| Transaction | TRAN-DESC | Transaction description | X(100) | Free text | `:9` |
| Transaction | TRAN-AMT | Transaction amount | S9(09)V99 | Positive = purchase, negative = refund/reversal | `:10` |
| Transaction | TRAN-MERCHANT-ID | Merchant number | 9(09) | Fixed `999999999` for bill payment | `:11`; `COBIL00C.cbl:226` |
| Transaction | TRAN-MERCHANT-NAME | Merchant name | X(50) | Fixed `BILL PAYMENT` for bill payment | `:12`; `COBIL00C.cbl:227` |
| Transaction | TRAN-MERCHANT-CITY | Merchant city | X(50) | Fixed `N/A` for bill payment | `:13`; `COBIL00C.cbl:228` |
| Transaction | TRAN-MERCHANT-ZIP | Merchant ZIP code | X(10) | Text | `:14` |
| Transaction | TRAN-CARD-NUM | Card number | X(16) | Foreign key→CARD-NUM | `:15` |
| Transaction | TRAN-ORIG-TS | Original timestamp | X(26) | `YYYY-MM-DD HH:MM:SS.000000` | `:16` |
| Transaction | TRAN-PROC-TS | Processing timestamp | X(26) | Same as above; AIX key (offset 304) | `:17`; `TRANFILE.jcl:84` |
| Daily transaction | DALYTRAN-* (13 fields) | Structurally identical to TRAN-* in the table above field by field (same order, type, and length) | 350 in total | Same as above; `PROC-TS` is written with the current timestamp on posting | `CVTRA06Y.cpy:5-17`; `CBTRN02C.cbl:436-438` |
### 2.4 TranCategoryBalance (TCATBALF / CVTRA01Y, 50 bytes) and DisclosureGroup (DISCGRP / CVTRA02Y, 50 bytes)

| Entity | Field name | Meaning | Type/Length | Value range/Enum | Source |
|---|---|---|---|---|---|
| Category balance | TRANCAT-ACCT-ID | Account number (key segment 1) | 9(11) | 11 characters with leading zeros | `CVTRA01Y.cpy:6` |
| Category balance | TRANCAT-TYPE-CD | Type code (key segment 2) | X(02) | `01`-`07` | `:7` |
| Category balance | TRANCAT-CD | Category code (key segment 3) | 9(04) | `0001`-`0005` | `:8` |
| Category balance | TRAN-CAT-BAL | Accumulated amount for this account under this type+category (interest calculation base) | S9(09)V99 | Amount | `:9`; `CBACT04C.cbl:465` |
| Disclosure group | DIS-ACCT-GROUP-ID | Disclosure group identifier (key segment 1) | X(10) | `A`/`DEFAULT`/`ZEROAPR` | `CVTRA02Y.cpy:6`; `discgrp.txt:1,18,35` |
| Disclosure group | DIS-TRAN-TYPE-CD | Type code (key segment 2) | X(02) | `01`-`07` | `:7` |
| Disclosure group | DIS-TRAN-CAT-CD | Category code (key segment 3) | 9(04) | `0001`-`0005` | `:8` |
| Disclosure group | DIS-INT-RATE | Annual interest rate (%) | S9(04)V99 | `00000{`=0.00, `00150{`=1.50, `00250{`=2.50 | `:9`; `discgrp.txt:1-4` |
### 2.5 TranType (TRANTYPE / CVTRA03Y, 60 bytes) and TranCategory (TRANCATG / CVTRA04Y, 60 bytes)

| Entity | Field name | Meaning | Type/Length | Value range/Enum | Source |
|---|---|---|---|---|---|
| Type | TRAN-TYPE | Type code (primary key) | X(02) | `01`-`07` | `CVTRA03Y.cpy:5` |
| Type | TRAN-TYPE-DESC | Type description | X(50) | Free text | `:6` |
| Category | TRAN-TYPE-CD | Type code (key segment 1, foreign key) | X(02) | `01`-`07` | `CVTRA04Y.cpy:6` |
| Category | TRAN-CAT-CD | Category code (key segment 2) | 9(04) | `0001`-`0005` | `:7` |
| Category | TRAN-CAT-TYPE-DESC | Category description | X(50) | Free text | `:8` |
### 2.6 SecUser (USRSEC / CSUSR01Y, 80 bytes, PK SEC-USR-ID) and AuthFraud (DB2 AUTHFRDS)

| Entity | Field name | Meaning | Type/Length | Value range/Enum | Source |
|---|---|---|---|---|---|
| User | SEC-USR-ID | Sign-on user ID (primary key) | X(08) | Uppercased at sign-on | `CSUSR01Y.cpy:18`; `COSGN00C.cbl:132` |
| User | SEC-USR-FNAME | First name | X(20) | Free text | `:19` |
| User | SEC-USR-LNAME | Last name | X(20) | Free text | `:20` |
| User | SEC-USR-PWD | Password (stored and compared in plain text) | X(08) | Both the stored value and the input are uppercased before the equality comparison | `:21`; `COSGN00C.cbl:223` |
| User | SEC-USR-TYPE | User type | X(01) | `A` administrator (→COADM01C) / `U` regular user (→COMEN01C) | `:22`; `COSGN00C.cbl:227-238` |
| Fraud | CARD_NUM | Card number (primary key 1) | CHAR(16) NOT NULL | Same as CARD-NUM | `AUTHFRDS.ddl:2` |
| Fraud | AUTH_TS | Authorization timestamp (primary key 2) | TIMESTAMP NOT NULL | Timestamp | `:3` |
| Fraud | AUTH_TYPE | Authorization type | CHAR(4) | Original request value `PA-RQ-AUTH-TYPE` | `:4` |
| Fraud | CARD_EXPIRY_DATE | Card expiry date | CHAR(4) | Original request value | `:5` |
| Fraud | MESSAGE_TYPE | Message type | CHAR(6) | Original request value | `:6` |
| Fraud | MESSAGE_SOURCE | Message source | CHAR(6) | Original request value | `:7` |
| Fraud | AUTH_ID_CODE | Authorization ID code | CHAR(6) | Taken from the authorization time | `:8`; `COPAUA0C.cbl:665` |
| Fraud | AUTH_RESP_CODE | Authorization response code | CHAR(2) | `00` approved / `05` declined | `:9`; `COPAUA0C.cbl:688,693` |
| Fraud | AUTH_RESP_REASON | Decline reason code | CHAR(4) | `0000`/`3100`/`4100`/`4200`/`4300`/`5100`/`5200`/`9000` | `:10`; `COPAUA0C.cbl:698-716` |
| Fraud | PROCESSING_CODE | Processing code | CHAR(6) | Original request value | `:11` |
| Fraud | TRANSACTION_AMT | Transaction amount | DECIMAL(12,2) | Amount | `:12` |
| Fraud | APPROVED_AMT | Approved amount | DECIMAL(12,2) | Approved = transaction amount, declined = 0 | `:13`; `COPAUA0C.cbl:689-695` |
| Fraud | MERCHANT_CATAGORY_CODE | Merchant category code (MCC; the column name is spelled as in the source) | CHAR(4) | Original request value | `:14` |
| Fraud | ACQR_COUNTRY_CODE | Acquirer country code | CHAR(3) | Original request value | `:15` |
| Fraud | POS_ENTRY_MODE | POS entry mode | SMALLINT | Original request value | `:16` |
| Fraud | MERCHANT_ID | Merchant number | CHAR(15) | Original request value | `:17` |
| Fraud | MERCHANT_NAME | Merchant name | VARCHAR(22) | Original request value | `:18` |
| Fraud | MERCHANT_CITY | Merchant city | CHAR(13) | Original request value | `:19` |
| Fraud | MERCHANT_STATE | Merchant state | CHAR(02) | Original request value | `:20` |
| Fraud | MERCHANT_ZIP | Merchant ZIP code | CHAR(09) | Original request value | `:21` |
| Fraud | TRANSACTION_ID | Transaction ID | CHAR(15) | Original request value | `:22` |
| Fraud | MATCH_STATUS | Match status | CHAR(1) | `P` pending / `D` declined at authorization / `E` pending expired / `M` matched to posting | `:23`; `CIPAUDTY.cpy:45-49` |
| Fraud | AUTH_FRAUD | Fraud flag | CHAR(1) | `F` fraud confirmed / `R` fraud removed | `:24`; `COPAUS2C.cbl:81-82,137` |
| Fraud | FRAUD_RPT_DATE | Fraud report date | DATE | CURRENT DATE | `:25`; `COPAUS2C.cbl:225` |
| Fraud | ACCT_ID | Account number | DECIMAL(11) | Logical foreign key→ACCT-ID | `:26` |
| Fraud | CUST_ID | Customer number | DECIMAL(9) | Logical foreign key→CUST-ID | `:27` |
### 2.7 TransactionType / TransactionTypeCategory (DB2) and ExportRecord (EXPORT.DATA / CVEXPORT, 500 bytes)

| Entity | Field name | Meaning | Type/Length | Value range/Enum | Source |
|---|---|---|---|---|---|
| DB2 type | TR_TYPE | Transaction type code (primary key) | CHAR(2) | `01`-`07` | `TRNTYPE.ddl:2` |
| DB2 type | TR_DESCRIPTION | Type description | VARCHAR(50) | Free text | `:3` |
| DB2 category | TRC_TYPE_CODE | Type code (primary key 1, foreign key→TR_TYPE) | CHAR(2) | `01`-`07` | `TRNTYCAT.ddl:2` |
| DB2 category | TRC_TYPE_CATEGORY | Category code (primary key 2) | CHAR(4) | `0001`-`0005` | `:3` |
| DB2 category | TRC_CAT_DATA | Category description (stored in uppercase, e.g. `REGULAR SALES DRAFT`) | VARCHAR(50) | Free text | `:4`; `DB2LTCAT.ctl:16` |
| Export | EXPORT-REC-TYPE | Record type | X(1) | `C` customer / `A` account / `X` cross-reference / `T` transaction / `D` card | `CVEXPORT.cpy:10`; `CBEXPORT.cbl:274,343,407,462,527` |
| Export | EXPORT-TIMESTAMP | Export timestamp (with DATE/TIME redefines) | X(26) | `YYYY-MM-DD` + separator + `HH.MM.SS…` | `:11-15` |
| Export | EXPORT-SEQUENCE-NUM | Record sequence number (export KSDS key area) | 9(9) COMP | Incrementing | `:16`; `CBEXPORT.cbl:277` |
| Export | EXPORT-BRANCH-ID | Branch number | X(4) | Fixed `0001` | `:17`; `CBEXPORT.cbl:278` |
| Export | EXPORT-REGION-CODE | Region code | X(5) | Fixed `NORTH` | `:18`; `CBEXPORT.cbl:279` |
| Export | EXPORT-RECORD-DATA | Union of the five record bodies (customer/account/transaction/cross-reference/card) | X(460), 5 REDEFINES | Interpreted according to REC-TYPE | `:19-100` |
## 3. Enums and code sets

State enums are already given in the respective field rows of §2: account status `Y`/`N` (`COACTUPC.cbl:193`), card status `Y`/`N` (`COCRDUPC.cbl:91`), user type `A`/`U` (`COCOM01Y.cpy:27-28`), primary card indicator `Y`/`N` (`COACTUPC.cbl:350`).
### 3.1 Authorization response codes and decline reason codes (`COPAUA0C.cbl`)

| Code | Meaning | Trigger condition | Evidence |
|---|---|---|---|
| `00` | Approved | No decline branch triggered; approved amount = transaction amount | `:692-695` |
| `05` | Declined | Any decline condition triggered; approved amount set to 0 | `:685-690` |
| `0000` | No decline reason (on approval) | Default assignment | `:698` |
| `3100` | Card/account/customer not found | `CARD-NFOUND-XREF`, `NFOUND-ACCT-IN-MSTR`, `NFOUND-CUST-IN-MSTR` | `:700-704` |
| `4100` | Insufficient available credit | `INSUFFICIENT-FUND` (transaction amount > limit − used credit) | `:705-706`, `:668-679` |
| `4200` | Card not active | `CARD-NOT-ACTIVE` — never set true in the source | `:707-708` |
| `4300` | Account closed | `ACCOUNT-CLOSED` — never set true in the source | `:709-710` |
| `5100` | Card fraud | `CARD-FRAUD` — never set true in the source | `:711-712` |
| `5200` | Merchant fraud | `MERCHANT-FRAUD` — never set true in the source | `:713-714` |
| `9000` | Other | `WHEN OTHER` | `:715-716` |
### 3.2 Posting validation failure codes (`CBTRN02C.cbl`, written into the 76-byte description segment of the DALYREJS record)

| Code | Meaning | Evidence |
|---|---|---|
| `100` | INVALID CARD NUMBER FOUND (card number not in CCXREF) | `:385-387` |
| `101` | ACCOUNT RECORD NOT FOUND (XREF points to a non-existent account) | `:397-399` |
| `102` | OVERLIMIT TRANSACTION (category net amount + this one > credit limit) | `:403-413` |
| `103` | TRANSACTION RECEIVED AFTER ACCT EXPIRATION (transaction date > account expiry date) | `:414-419` |
| `109` | ACCOUNT RECORD NOT FOUND (INVALID KEY on the account update) | `:556-558` |
### 3.3 Transaction type codes (`trantype.txt`, all 7 records; the trailing 8 bytes are `00000000` padding)

`01` Purchase (`trantype.txt:1`), `02` Payment (`:2`), `03` Credit (`:3`), `04` Authorization (`:4`), `05` Refund (`:5`), `06` Reversal (`:6`), `07` Adjustment (`:7`).
### 3.4 Transaction category codes (`trancatg.txt`, all 18 records; key = type 2 + category 4)

| Type | Category | Meaning | Original English value | Evidence |
|---|---|---|---|---|
| 01 | 0001 | Regular purchase draft | Regular Sales Draft | `trancatg.txt:1` |
| 01 | 0002 | Regular cash advance | Regular Cash Advance | `:2` |
| 01 | 0003 | Convenience check debit | Convenience Check Debit | `:3` |
| 01 | 0004 | ATM cash advance | ATM Cash Advance | `:4` |
| 01 | 0005 | Interest (used by interest accrual transactions) | Interest Amount | `:5`; `CBACT04C.cbl:484` |
| 02 | 0001 | Cash payment | Cash payment | `:6` |
| 02 | 0002 | Electronic payment (used by bill payment) | Electronic payment | `:7`; `COBIL00C.cbl:221` |
| 02 | 0003 | Check payment | Check payment | `:8` |
| 03 | 0001 | Credit to account | Credit to Account | `:9` |
| 03 | 0002 | Credit to purchase balance | Credit to Purchase balance | `:10` |
| 03 | 0003 | Credit to cash balance | Credit to Cash balance | `:11` |
| 04 | 0001 | Zero-dollar authorization | Zero dollar authorization | `:12` |
| 04 | 0002 | Online purchase authorization | Online purchase authorization | `:13` |
| 04 | 0003 | Travel booking authorization | Travel booking authorization | `:14` |
| 05 | 0001 | Refund credit | Refund credit | `:15` |
| 06 | 0001 | Fraud reversal | Fraud reversal | `:16` |
| 06 | 0002 | Non-fraud reversal | Non-fraud reversal | `:17` |
| 07 | 0001 | Sales draft credit adjustment | Sales draft credit adjustment | `:18` |

**Verification note**: the actual maximum category code is `0005` (used only by type `01`); **`0006` does not exist**; the 300 records of `dailytran.txt` contain only `01-0001` (250 records) and `03-0001` (50 records), and all 50 records of `tcatbal.txt` are `01-0001`.
## 4. Identifier rules (leading zeros and character types)

| Identifier | Definition | Rule | Evidence |
|---|---|---|---|
| ACCT-ID | `PIC 9(11)` | 11 numeric characters, right-aligned with leading zeros; screen input goes through `NUMVAL` and is then written back; VSAM key `KEYS(11 0)` | `CVACT01Y.cpy:5`; `ACCTFILE.jcl:40`; `COTRN02C.cbl:203-206` |
| CUST-ID | `PIC 9(09)` | 9 numeric characters with leading zeros; unrelated to the sign-on user ID (X(8)) | `CVCUS01Y.cpy:5`; `CUSTFILE.jcl:50` |
| XREF-CUST-ID | `PIC 9(09)` | Same domain as CUST-ID, foreign key | `CVACT03Y.cpy:6` |
| CARD-NUM | `PIC X(16)` | **Character type, not numeric**; 16-digit numeric string, must not lose leading zeros after `NUMVAL`; passed as the original X(16) when used as a CICS key | `CVACT02Y.cpy:5`; `COCRDSLC.cbl:189` |
| TRAN-ID | `PIC X(16)` | Character type, 16 characters; generated by `READPREV` to take the maximum key then +1; system transaction = `PARM date(10)` + sequence number | `CVTRA05Y.cpy:5`; `COTRN02C.cbl:444-451`, `COBIL00C.cbl:212-219` |
| TRANCAT composite key | 9(11)+X(2)+9(4) | Concatenated 17-byte key, with leading zeros preserved in the account number | `CVTRA01Y.cpy:5-8`; `TCATBALF.jcl:40` |
| Date fields | `PIC X(10)` | `YYYY-MM-DD` text; comparisons rely on lexicographic order (not a date type) | `CVACT01Y.cpy:10-12`; `CBTRN02C.cbl:414` |
## 5. Legacy ASCII numeric fields use EBCDIC overpunch

`app/data/ASCII/*.txt` are not plain text numeric files: signed numeric fields (`PIC S9(n)V99`) retain the **overpunched trailing byte** of EBCDIC zoned decimal — the last byte packs the sign onto the digit zone: `{`=+0, `}`=-0, `A`-`I`=+1…+9, `J`-`R`=-1…-9. Reading them as plain ASCII yields illegal numeric values (`NUMVAL` returns 0 or raises a runtime exception), so **every field must be decoded according to the copybook before importing into the new system** (remove the overpunch→restore the sign and the last digit→then convert to decimal).

Real example (`app/data/ASCII/acctdata.txt` line 1, 300 bytes in total): `00000000001Y00000001940{00000020200{00000010200{2014-11-202025-05-202025-05-2000000000000{00000000000{A000000000 …`

| Offset | Field | Raw bytes | Decoded result |
|---|---|---|---|
| 1-11 | ACCT-ID | `00000000001` | 1 |
| 12 | ACCT-ACTIVE-STATUS | `Y` | Valid |
| 13-24 | ACCT-CURR-BAL | `00000001940{` | **1940.00** (`{`=+0 → last digit 0, positive sign) |
| 25-36 | ACCT-CREDIT-LIMIT | `00000020200{` | 20200.00 |
| 37-48 | ACCT-CASH-CREDIT-LIMIT | `00000010200{` | 10200.00 |
| 49-58 | ACCT-OPEN-DATE | `2014-11-20` | 2014-11-20 |

Second example (`dailytran.txt`): line 1 `TRAN-AMT` (offset 133-143) = `0000005047G` → **504.77** (`G`=+7); line 2 = `0000009190}` → **-919.00** (`}`=-0, corresponding to the "Return item" refund). Full set of affected fields: `ACCT-CURR-BAL`, `ACCT-CREDIT-LIMIT`, `ACCT-CASH-CREDIT-LIMIT`, `ACCT-CURR-CYC-CREDIT`, `ACCT-CURR-CYC-DEBIT`, `TRAN-AMT`/`DALYTRAN-AMT`, `TRAN-CAT-BAL`, `DIS-INT-RATE` (e.g. `00150{`=1.50 in `discgrp.txt`). Unsigned fields such as `ACCT-ID`, `CUST-ID`, `CARD-NUM` are ordinary character numerics and **have no overpunch**.
## 6. Unresolved items

| Item | Status | Evidence |
|---|---|---|
| Values of `PA-AUTH-STATUS` (IMS root segment) | The field is defined but there is no assignment statement anywhere in the repository, so its semantics are unconfirmed | `CIPAUSMY.cpy:21` |
| Readers/writers of `TRANSACTION_TYPE_CATEGORY` | No COBOL SQL reference; only loaded by `DB2LTCAT.ctl` and extracted by `TRANEXTR.jcl` | `DB2LTCAT.ctl:15`, `TRANEXTR.jcl:112` |
| Fallback rule between an empty `ACCT-GROUP-ID` and the `DEFAULT` group | The sample account GROUP is empty while DISCGRP uses `A`/`DEFAULT`/`ZEROAPR`; the fallback logic appears only in `1200-GET-INTEREST-RATE`, with no explanatory comment | `CBACT04C.cbl:415-460` |
| Export file primary key `KEYS(4 28)` | It is 1 byte off from `EXPORT-SEQUENCE-NUM` (offset 27), a legacy defect | `CBEXPORT.jcl:32`, `CVEXPORT.cpy:16` |
