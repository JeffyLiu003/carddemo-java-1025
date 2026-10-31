# ADR-005: Cross-Service Consistency and Idempotency Strategy

| Item | Content |
|----|------|
| ID | ADR-005 |
| Date | 2026-10-25 |
| Status | Accepted |
| Related requirements | BR-BILL-05, BR-POST-06, BR-POST-08, NR-POST-01 |

## Context

Bill payment and end-of-day posting need to write both the "transaction" and the "account balance". The legacy system guarantees atomicity through a single CICS transaction;
after the split the two writes live in different databases, so a consistency approach must be chosen and failure/retry semantics defined.

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| Option A: local transaction + synchronous call + rollback on failure + idempotency key | Write the local transaction first (status PENDING), set it to POSTED after the downstream call succeeds; roll back locally on failure; every transaction carries a unique business number | Simple to implement; no middleware; failures are visible | Depends on downstream availability; in a very short window there can be an inconsistency where the downstream has executed but the local transaction rolled back |
| Option B: transactional outbox + message queue eventual consistency | Write the transaction and the outbox locally, deliver account postings asynchronously; the consumer side is idempotent | Decoupled; downstream failures can be retried | Requires messaging middleware and reconciliation jobs; high complexity for the demo environment |
| Option C: Saga orchestration + compensating transactions | Explicit compensation (reverse posting) | Long transactions are recoverable | Complex compensation rules; high reconciliation and audit cost |

## Quantitative Trade-off Table

| Option | Implementation cost | Performance overhead | Operational agility | Auditability | Risk |
|---|---|---|---|---|---|
| A | Low | Low (1 synchronous call) | Failures visible within seconds | High (the transaction state machine is queryable) | Medium (short-window inconsistency) |
| B | High | Medium (asynchronous + consumer latency) | Eventual consistency within minutes | High (the outbox is auditable) | Low (reconciliation required) |
| C | Very high | Medium-high | Within minutes | High | Medium |

## Decision

This phase adopts Option A and enforces three constraints:

1. Every transaction uses `transactionId` as the idempotency key; duplicate submissions return the existing result (NR-POST-01).
2. Bill payment: create the transaction first (PENDING) → call `POST /accounts/{id}/payments` → set it to POSTED locally;
   if the downstream call fails, the local transaction rolls back, the client receives 503 and can retry with the same request.
3. Posting: when a single "category accumulation + account posting" fails, that item moves to `REJECTED` with the reason recorded, without interrupting the whole batch;
   the run result returns `processed/posted/rejected` counts (aligned with the semantics of BR-POST-08 return code 4).

Option B is the next-stage evolution (Outbox + Kafka/RabbitMQ); by then the account side must support idempotent consumption.

## Rationale

For the demo and the first release, "observable, retryable, reconcilable" takes priority; the synchronous call chain is short, so failure probability and recovery cost stay manageable.
The reconciliation job compares the end-of-day snapshots of `transaction` and `account` as a safety net (see the rollback path).

## Consequences (Impact and Rollback Path)

- Impact: bill payment returns 503 when the downstream is unavailable; the posting batch reports partial success.
- Rollback: if cross-database inconsistency occurs, perform reverse posting (reversal) based on end-of-day reconciliation differences, with `transaction` as the authority;
  when switching to Option B later, the existing APIs and idempotency key semantics are preserved.

## Evidence

- `COBIL00C.cbl:211-235` (legacy writes the transaction and the account in the same transaction)
- `CBTRN02C.cbl:229-231,424-444` (return code 4 for partial success)
- `docs/design/03-service-decomposition.md` §4
