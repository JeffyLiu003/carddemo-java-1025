# ADR-001: Split into 6 Microservices by DDD Bounded Context

| Item | Content |
|----|------|
| ID | ADR-001 |
| Date | 2026-10-25 |
| Status | Accepted |
| Related requirements | L1 capabilities C1–C15; `docs/legacy/02-five-level-model.md` |

## Context

The legacy system is a CICS monolith (44 COBOL programs, 21 BMS screens, 12 VSAM files, 3 DB2 tables, 2 IMS segments).
Its business capabilities span six semantic areas — customer, account, card, transaction, authorization, and user — but the programs share state through a 2000-byte COMMAREA
and read/write across files directly (for example, the account update writes both ACCTDAT and CUSTDAT), with no explicit boundaries.
The migration must determine the service granularity without losing business semantics, avoiding both "split by program" (44 services) and "lift and shift the whole monolith" (1 service).

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| Option A: split into 6 services by DDD bounded context | IAM, Customer, Card, Account, Transaction, Authorization | Boundaries align with business capabilities; clear data ownership; each service can evolve independently; teams can work in parallel | Cross-service use cases need orchestration; eventual consistency must be handled |
| Option B: one-to-one split by COBOL program family | One service per online/batch program (44) | Most intuitive migration mapping | Explosion of distributed calls; fragmented transaction boundaries; no place for COMMAREA state; extremely high operational cost |
| Option C: monolith modularization (modular monolith) | Modules by package inside a single Spring Boot application | Simplest implementation and transactions; lowest initial cost | Cannot be deployed/scaled independently; does not match the "microservices architecture" goal |

## Quantitative Trade-off Table

| Option | Implementation cost | Performance overhead | Operational agility | Auditability | Risk |
|---|---|---|---|---|---|
| A (6 services) | High (6 sets of builds/images/databases) | Medium (2–3 cross-service HTTP calls per use case) | Independent releases at minute-level | High (independent data and logs per service) | Medium (distributed consistency) |
| B (44 services) | Very high | High (4+ hops per call chain) | Minute-level but complex orchestration | Medium (hard to trace when distributed tracing is missing) | High |
| C (modular monolith) | Low | Low | Requires whole-application release | High | Low (but evolution is constrained) |

## Decision

Adopt Option A: 6 bounded contexts / 6 independently deployable services, with a separate database per service.

## Rationale

1. The six contexts already form stable clusters of data and responsibility in the legacy code (`docs/legacy/01-codebase-inventory.md`).
2. Authorization (IMS/MQ) and transaction posting (batch) differ the most in load characteristics, change frequency, and compliance requirements, so they must be independently deployable.
3. Account/customer/card are shared master data; once separated they can be reused by the authorization and transaction services, avoiding the legacy pattern where "every program reads the VSAM files again".
4. User security and the business domain have entirely different concerns (security baseline vs accounting correctness) and should evolve independently.

## Consequences (Impact and Rollback Path) [Required for L3]

- Impact: cross-service synchronous calls, eventual data consistency, deployment dependencies (Compose health checks).
- Rollback: if cross-service calls become a bottleneck, Account+Transaction+Authorization can be merged into an "accounting domain service" (keeping package boundaries),
  with unchanged API contracts; Customer/Card/IAM stay independent. The merge only requires adjusting Maven modules and Compose orchestration.

## Evidence

- `docs/legacy/01-codebase-inventory.md` program/data inventory
- `docs/legacy/02-five-level-model.md` §1.2 capability inventory, §2.1 aggregates
- `docs/design/02-bounded-contexts.md`
