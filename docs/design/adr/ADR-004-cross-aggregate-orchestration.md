# ADR-004: Orchestration Ownership for Cross-Aggregate Use Cases (Account View / Account Maintenance / Bill Payment)

| Item | Content |
|----|------|
| ID | ADR-004 |
| Date | 2026-10-25 |
| Status | Accepted |
| Related requirements | BR-ACCT-07/08, BR-BILL-01..05, BR-TRN-01..06 |

## Context

Legacy screens put multiple aggregates in the same transaction:

1. One `COACTUPC` screen modifies Account and Customer at the same time (`READ UPDATE` on two files + two `REWRITE`s).
2. One `COACTVWC` screen displays the Account + Customer + Card mapping.
3. `COBIL00C` writes TRANSACT and deducts the ACCTDAT balance in one transaction.
4. `CBTRN02C` writes TRANSACT, TCATBALF and updates ACCTDAT in one batch run.

After the microservice split, cross-database transactions cannot be retained, so we must decide "who orchestrates, who executes, and what happens on failure".

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| Option A: callers (front end/BFF) compose queries; money-related use cases are orchestrated by transaction-service | Query composition stays at the edge; write use cases are concentrated in the service that owns the "transaction" aggregate | Clear responsibilities; minimal transaction boundaries; fits the business (payment is a transaction-context use case) | The front end must make multiple calls; the orchestrating service depends on downstream services |
| Option B: every service exposes "aggregate write" APIs and the front end orchestrates | Services are pure CRUD | Simple services | Business rules leak into the front end; consistency cannot be guaranteed; auditing is difficult |
| Option C: introduce a dedicated BFF/orchestration service | Adds a 7th service | The front end calls only once | An extra hop and another deployment; low benefit for a demo project |

## Quantitative Trade-off Table

| Option | Implementation cost | Performance overhead | Operational agility | Auditability | Risk |
|---|---|---|---|---|---|
| A | Medium | Medium (2–3 calls) | Immediate | High (every write has an explicit owner) | Medium (compensation required) |
| B | Low | Low | Immediate | Low | High (no consistency guarantee) |
| C | High | Medium-high | Immediate | High | Medium |

## Decision

Adopt Option A:

- **Queries**: the account view is composed by the caller from `GET /accounts/{id}` + `GET /customers/{cid}` + `GET /cards/by-account/{aid}`.
- **Account maintenance**: split into two use cases, `PUT /accounts/{id}` (account fields) and `PUT /customers/{id}` (customer fields);
  there is no longer a cross-aggregate transaction; the UI submits step by step and prompts according to error codes.
- **Bill payment**: `transaction-service` acts as the use-case orchestrator (read account → create payment transaction → call account to deduct);
  a write failure inside the local transaction rolls back.
- **End-of-day posting**: orchestrated by `transaction-service`; "validation + category accumulation" stays local, while "account balance change" calls account-service.

## Rationale

1. The authoritative record for payments and posting is the transaction (TRANSACT); the account balance is derived state that can be recomputed, so orchestration should belong to the transaction context.
2. Account and customer have two independent lifecycles (a customer can have multiple accounts); forcing them into one transaction is implementation coupling of the legacy screens.
3. The authorization context needs to "read an account snapshot + write its own pending-authorization totals", which naturally suits self-orchestration.

## Consequences (Impact and Rollback Path)

- Impact: the UI/front end must compose calls; inter-service network failure branches increase (uniform 503 + retry).
- Rollback: if the front end cannot compose calls, add a read-only BFF (Option C); existing APIs stay unchanged.

## Evidence

- `COACTUPC.cbl:2568-2580,3888-4104`
- `COACTVWC.cbl:723-869`
- `COBIL00C.cbl:211-235`
- `CBTRN02C.cbl:2000-POST-TRANSACTION 424-444`
