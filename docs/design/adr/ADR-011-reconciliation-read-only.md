# ADR-011: Read-Only Reconciliation and the Boundary of Difference Handling

| Item | Content |
|----|------|
| ID | ADR-011 |
| Date | 2026-10-26 |
| Status | Accepted |
| Related requirements | `docs/design/12-reconciliation-and-eod.md`, A1.4 |

## Context

A1.4 introduced reconciliation: comparing posting batches with general ledger journal entries to identify missing entries, amount mismatches, orphan entries, and trial balance imbalances.
After reconciliation finds differences, the system could:

1. Automatically modify business/general ledger data so both sides match;
2. Only record the differences and let a human confirm and adjust separately;
3. Block transactions outright.

The write-permission boundary of the reconciliation service and the responsibilities of EOD must be decided.

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| A: reconciliation can adjust entries automatically | Write reverse entries/change balances after finding differences | Highly automated | Reconciliation errors would damage accounting a second time; unacceptable for audit |
| B: read-only + difference registry + manual handling (adopted) | Reconciliation is read-only; differences are persisted and can be annotated; adjustments go through normal posting/manual entries | Clear audit trail; failures do not affect accounting | Requires a manual closed loop; difference handling and adjustment are two steps |
| C: reconciliation blocks transactions | Forbid new transactions until differences are cleared | Strong consistency | Poor availability; historical differences would block business for a long time |

## Decision

Adopt Option B:

1. `reconciliation-service` reads transaction posting batches and ledger journal entries and trial balance only via GET,
   and does not write any accounting data.
2. Differences are written to `reconciliation_item` with status `MISSING_JOURNAL`, `MISSING_BATCH`, `AMOUNT_MISMATCH`, `UNBALANCED`;
   `resolve` only updates `resolved` and `resolution_note` and does not trigger adjustments.
3. Real adjustments must go through transaction posting batches or ledger manual journal entries, preserving the full audit chain.
4. EOD serves as the job entry point and currently only orchestrates reconciliation; it does not close periods or change balances automatically.
5. The current reconciliation scope is "batch ↔ journal entry + trial balance"; account balance vs general ledger 1100 requires per-account sub-ledgers and is not included for now.

## Consequences

- Differences require manual follow-up; the system provides a "resolved" marker and notes to facilitate auditing.
- Reconciliation failures do not block transactions, consistent with the principle of "money facts first".
- If automatic adjustment is introduced later, it should be implemented as new posting batches/journal entries rather than directly changing balances.
- Reconciliation pulls data with paginated reads (500 per page, at most 40 pages); as data volume grows, filtering by date should be pushed down to the server side.

## Evidence

- `docs/design/12-reconciliation-and-eod.md` §2, §5
- `docs/design/adr/ADR-010-ledger-and-posting-separation.md`
- `services/reconciliation-service/src/main/java/com/carddemo/reconciliation/application/ReconciliationService.java`
