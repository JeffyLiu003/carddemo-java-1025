# ADR-010: Separation of Responsibilities between the Posting Engine and the General Ledger

| Item | Content |
|----|------|
| ID | ADR-010 |
| Date | 2026-10-26 |
| Status | Accepted |
| Related requirements | `docs/design/11-money-path-slice-a2.md`, A1.2/A1.3 |

## Context

A1.2 introduced posting batches (business transaction → debit/credit entries) and A1.3 introduced the general ledger (accounts, journal entries, trial balance).
The following must be decided:

1. Whether posting entries and general ledger journal entries live in the same service/database;
2. The failure boundaries among account balance changes, batch persistence, and general ledger recording;
3. How reversal keeps both sides consistent and auditable.

Constraints: the existing system has no MQ; account balances live in account-service and transactions in transaction-service; the demo environment requires
a single-command start and repeatable demos.

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| A: merge into transaction-service | Posting batches write general ledger accounts/entries directly | Single-database transaction, strong consistency; simplest to implement | General ledger coupled to transactions; cannot close the period or audit independently; violates bounded contexts |
| B: independent ledger-service + synchronous call (alert on failure) | transaction updates the account and persists the batch first, then writes the general ledger over HTTP; the ledger is idempotent | Clear boundaries; trial balance/period close can run independently; failures do not affect the money facts | Eventual consistency; the ledger may briefly lack entries and needs compensation/reconciliation |
| C: Outbox + MQ | Write to the outbox when persisting the batch; the ledger subscribes to events | Fully decoupled; replayable; high throughput | Requires MQ and consumption governance; high cost for this phase |

## Quantitative Trade-offs

| Option | Implementation cost | Consistency | Auditability | Operational complexity | Risk |
|---|---|---|---|---|---|
| A | Low | Strong | Medium (transactions and general ledger mixed together) | Low | General ledger evolution is constrained |
| B | Medium | Eventual (seconds) | High (accounts/periods/entries are independent) | Medium | General ledger write failures require alerting and compensation |
| C | High | Eventual (replayable) | High | High | Message backlog/duplicate consumption |

## Decision

Adopt Option B and implement:

1. **Separation of responsibilities**: transaction-service owns business transactions, posting types/rules/batches; ledger-service owns the chart of accounts,
   journal entries, and periods. Each side has its own database, and cross-database JOINs are forbidden.
2. **Call order**: validate → call account-service to change the balance → save the batch and legs locally → call ledger to write the journal entry.
   An exception in the general ledger call only produces a `WARN`, and the batch keeps `ledger_entry_id = null`.
3. **Idempotency**: posting batches are unique by `idempotencyKey`; general ledger entries are unique by `(sourceType, sourceId)`;
   reversal batches/reversal entries each use a deterministic business key, and duplicate requests return the existing record.
4. **Reversal**: original records are never deleted. The posting side creates a reversal batch with swapped directions and marks the original batch `REVERSED`;
   the ledger side creates a reverse entry with `sourceType=REVERSAL` and marks the original entry `REVERSED`.
5. **Period close**: the general ledger only allows recording during OPEN periods; closing a period does not back-pressure the transaction service (see Consequences).

## Rationale

1. Money facts (account balances, posting batches) and the accounting projection (general ledger) have different failure domains; merging them would let reporting/period close block transactions.
2. The general ledger needs to evolve independently (multi-currency, consolidated reports, audit), so a separate service boundary is more stable.
3. Synchronous calls match the style of ADR-005, so the full chain can be demonstrated without introducing MQ; the idempotency keys are reserved for future Outbox replay.

## Consequences (Impact and Rollback)

- **Eventual consistency**: the general ledger may briefly lack an entry; an empty `ledger_entry_id` can serve as the scan condition for the compensation job.
- **Period close does not block transactions**: business postings can still occur in a closed period, in which case the general ledger sync fails and raises an alert; going to production requires
  a "freeze transactions before period close" or "automatically carry over to the next period" strategy.
- **Trial balance scope**: aggregated by period movement, including original entries and reversal entries; opening balances are excluded.
- **Rollback**: setting `ledger.sync-enabled` to false stops general ledger synchronization; transactions and accounts are unaffected. To switch to Option C,
  only the synchronous call needs to be replaced by outbox publishing, with the interface and idempotency keys unchanged.

## Evidence

- `docs/design/11-money-path-slice-a2.md` §2, §3
- `docs/design/03-service-decomposition.md` §3, §5
- `services/transaction-service/src/main/java/com/carddemo/transaction/application/PostingBatchService.java`
- `services/ledger-service/src/main/java/com/carddemo/ledger/application/JournalEntryApplicationService.java`
