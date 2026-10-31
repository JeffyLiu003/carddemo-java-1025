# ADR-006: Concurrency Control Switched from "Record Lock + Snapshot Comparison" to Optimistic Locking

| Item | Content |
|----|------|
| ID | ADR-006 |
| Date | 2026-10-25 |
| Status | Accepted |
| Related requirements | BR-ACCT-06, BR-CARD-10 |

## Context

Legacy online maintenance programs lock the record with `READ ... UPDATE` when reading data, and on save they compare the screen snapshot field by field
against the current values in the database, prompting `Record changed by some one else. Please review` on mismatch (`COACTUPC.cbl:4109-4191`,
`COCRDUPC.cbl:1498-1520`). That model relies on long CICS transactions, and record locks span the user's thinking time; a web/microservice architecture
cannot hold database locks waiting for user actions.

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| Option A: JPA `@Version` optimistic locking | Validate the version number on every update; return 409 on conflict | No long-transaction locks; simple to implement; semantically equivalent (detects concurrent modification) | Clients must send the version back; conflicts require the user to retry |
| Option B: pessimistic locking (`SELECT FOR UPDATE`) | Short lock within the request | Simple when the conflict rate is low | Still cannot cover the "user thinking time"; prone to deadlocks; poor throughput |
| Option C: field-level merge + audit | Record field changes and merge conflicts automatically | Good user experience | Complex business semantics (balances cannot be merged automatically); high implementation cost |

## Quantitative Trade-off Table

| Option | Implementation cost | Performance overhead | Operational agility | Auditability | Risk |
|---|---|---|---|---|---|
| A | Low | Low | Immediate | High (version numbers are traceable) | Low |
| B | Low | Medium-high (lock waits) | Immediate | Medium | Medium-high (deadlocks/timeouts) |
| C | High | Medium | Immediate | High | Medium (merge errors) |

## Decision

Adopt Option A: every aggregate table has `version BIGINT` and entities use `@Version`; update requests may carry `version`,
and a conflict returns `409 CONCURRENT_UPDATE`, with the legacy message text preserved.

## Rationale

1. Semantic equivalence: the legacy "snapshot comparison" is essentially optimistic concurrency detection.
2. Balances and credit limits cannot be merged automatically, so callers must re-read and retry.
3. Database independent: PostgreSQL/H2 behave the same, which simplifies testing.

## Consequences (Impact and Rollback Path)

- Impact: update APIs must return `version`; the front end prompts for a refresh on conflict.
- Rollback: if a hot account with frequent conflicts appears, the single-account posting API can switch to short database row locks (`SELECT FOR UPDATE`),
  while the other APIs keep optimistic locking.

## Evidence

- `COACTUPC.cbl:4109-4191` (9700-CHECK-CHANGE-IN-REC)
- `COCRDUPC.cbl:1498-1520` (9300 concurrent comparison)
- `@Version` in `Account.java` / `Card.java`
