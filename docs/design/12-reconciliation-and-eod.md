# 12 Money Path Slice A1.4: Reconciliation and EOD

> Upstream: `11-money-path-slice-a2.md` (A1.4)
> Status: implemented (A1.4)

## 1. Goals

Verify that the two chains — "business posting batch" and "general ledger journal entry" — are complete and consistent in amount, and turn breaks into records that can be tracked and handled;
provide an EOD job entry point that reserves orchestration slots for interest accrual, statements and general ledger closing later.

```
transaction posting_batch ──┐
                            ├─ reconciliation (by business date) → reconciliation_run / item → break registration/handling
ledger journal_entry ───────┘
                            └─ trial balance (by period) → balanced?
EOD run → triggers reconciliation → records the job result
```

## 2. Reconciliation Basis

- **Business date**: the date part of `posting_batch.created_at` and `journal_entry.posted_at`.
- **Match key**: `posting_batch.batch_id` ↔ `journal_entry.source_id` (only `sourceType=POSTING_BATCH`).
- **Amount**: batch amount = total journal entry debits = total credits.
- **Orphans**: a journal entry without a batch (`MISSING_BATCH`); a batch without a journal entry (`MISSING_JOURNAL`).
- **Trial balance**: `Σ debit = Σ credit` for the period, otherwise `UNBALANCED`.
- Manual journal entries (`sourceType=MANUAL`) do not participate in batch matching and do not affect the batch reconciliation outcome.

## 3. Service and Tables (reconciliation-service, :8089, DB `carddemo_reconciliation`)

### 3.1 `reconciliation_run`

`run_id`, `business_date`, `period`, `status` (RUNNING/COMPLETED/FAILED), `started_at`, `completed_at`,
`batch_count`, `batch_amount`, `journal_count`, `journal_debit_total`, `journal_credit_total`,
`match_count`, `mismatch_count`, `missing_journal_count`, `missing_batch_count`, `matched`, `error_message`, `version`.

### 3.2 `reconciliation_item`

`item_id`, `run_id`, `check_type` (POSTING_VS_JOURNAL/TRIAL_BALANCE), `reference_id`,
`expected_amount`, `actual_amount`, `difference`, `status`
(MATCH/MISSING_JOURNAL/MISSING_BATCH/AMOUNT_MISMATCH/UNBALANCED), `resolved`, `resolution_note`, `detail`, `version`.

### 3.3 `eod_run`

`run_id`, `business_date`, `status` (RUNNING/COMPLETED/FAILED), `reconciliation_run_id`,
`started_at`, `completed_at`, `message`, `version`.

## 4. API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/reconciliation-runs` | Run reconciliation for `businessDate`, 201 |
| GET | `/api/v1/reconciliation-runs/{runId}` | Run details + items |
| GET | `/api/v1/reconciliation-runs?page=&size=` | Run history |
| GET | `/api/v1/reconciliation-runs/{runId}/items` | Break details |
| POST | `/api/v1/reconciliation-items/{itemId}/resolve` | Record a break resolution note |
| POST | `/api/v1/eod-runs` | Run EOD (current step: reconciliation), 201 |
| GET | `/api/v1/eod-runs/{runId}` | EOD details |
| GET | `/api/v1/eod-runs?page=&size=` | EOD history |

Reconciliation request:

```json
{"businessDate":"2026-10-26"}
```

Response summary:

```json
{"runId":"...","businessDate":"2026-10-26","period":"2026-10","status":"COMPLETED",
 "matched":true,"batchCount":2,"batchAmount":50.00,"journalCount":2,
 "matchCount":3,"mismatchCount":0,"missingJournalCount":0,"missingBatchCount":0,
 "items":[{"checkType":"POSTING_VS_JOURNAL","referenceId":"b-1","status":"MATCH",
           "expectedAmount":25.00,"actualAmount":25.00,"difference":0.00}]}
```

Break handling:

```json
{"note":"Confirmed general ledger sync delay, manually backfilled"}
```

## 5. Dependencies and Failure Semantics

- transaction-service: reads `/api/v1/posting-batches?page=&size=` page by page and filters by business date.
- ledger-service: reads `/api/v1/journal-entries?page=&size=` page by page and filters by posting date;
  reads `/api/v1/trial-balance?period=YYYY-MM`.
- Downstream unavailable → the run is marked FAILED and `error_message` is kept; the API returns 503 `DEPENDENCY_UNAVAILABLE`.
- Reconciliation only reads business and general ledger data and modifies no accounting data; break handling only updates reconciliation records.

## 6. Frontend

- A new `#/reconciliation` item in the admin menu:
  - Business date + "Run reconciliation" / "Run EOD";
  - Reconciliation history (status, matched, batch/journal counts, break count);
  - Run details (summary + items table + "Record resolution" on unmatched items).
- Home page admin entry updated in sync; the proxy gains reconciliation/EOD paths.

## 7. Acceptance

1. When posting batches and general ledger journal entries correspond one to one, `matched=true` and `mismatchCount=0`.
2. When a journal entry is deleted/missing (simulated), `MISSING_JOURNAL` appears; when there is an extra journal entry, `MISSING_BATCH` appears.
3. When amounts differ, `AMOUNT_MISMATCH` appears with a correct `difference`.
4. When the trial balance is off, `UNBALANCED` appears with `matched=false`.
5. A break item accepts a resolution note that survives a refresh.
6. An EOD run record is linked to its reconciliation run, and its success/failure status is queryable.
7. The frontend can complete: run reconciliation → view breaks → record resolution → view EOD history.

## 8. Follow-ups

- Reconciling account balances against general ledger 1100 requires a per-account sub-ledger mapping (the general ledger currently stops at the ledger account level).
- Extend the EOD job graph: interest accrual → fees → statements → past-due roll → general ledger closing → reconciliation → reports.
