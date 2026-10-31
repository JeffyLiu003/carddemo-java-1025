# ADR-003: Data Ownership and "One Database per Service"

| Item | Content |
|----|------|
| ID | ADR-003 |
| Date | 2026-10-25 |
| Status | Accepted |
| Related requirements | ADR-001; `docs/design/06-traceability-matrix.md` |

## Context

In the legacy system, several programs read and write across files directly: `COACTUPC` updates ACCTDAT and CUSTDAT at the same time; `CBTRN02C` directly
updates ACCTDAT and TCATBALF; `COPAUA0C` directly reads ACCTDAT/CUSTDAT/CCXREF. After moving to microservices, if cross-database direct access were still allowed,
a "distributed monolith" would form again. The single owner of every table must be made explicit.

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| Option A: separate database per service | 6 PostgreSQL databases, cross-database access forbidden | Boundaries enforced; can be migrated/scaled/rolled back independently | Cross-domain queries need API composition; consistency issues move upstream |
| Option B: shared database, isolated by schema | One database with multiple schemas | Simple migration; cross-schema queries possible | Boundaries softened and easily bypassed; lock contention and version coupling |
| Option C: shared database, shared tables | Keep the legacy single database | Least effort | Cannot be deployed independently; violates the microservices goal |

## Quantitative Trade-off Table

| Option | Implementation cost | Performance overhead | Operational agility | Auditability | Risk |
|---|---|---|---|---|---|
| A | Medium (6 databases + initialization scripts) | Medium (cross-domain queries go over HTTP) | A single service can be rolled back independently | High (clear data responsibility) | Medium (consistency) |
| B | Low | Low | Requires a shared-database change window | Medium | Medium-high (boundary erosion) |
| C | Lowest | Lowest | Whole-application release | Low | High |

## Decision

Adopt Option A: six independent databases — `carddemo_iam`, `carddemo_customer`, `carddemo_card`, `carddemo_account`,
`carddemo_transaction`, `carddemo_authorization` — and services interact only through REST.

## Rationale

1. The mapping between aggregate roots and tables is stable (see the data dictionary), and each table has exactly one writer.
2. In the legacy system, "cross-file transactions" are concentrated in a few use cases (account + customer maintenance, bill payment, posting), whose orchestration ownership is already defined by ADR-004/005.
3. The demo environment runs 6 databases on a single PostgreSQL instance, keeping operational cost manageable while preserving "logical isolation".

## Consequences (Impact and Rollback Path)

- Impact: composite queries (account view, authorization decisions) require multiple HTTP calls; reports can only read the transaction database.
- Rollback: if joint queries become mandatory, introduce read-only data synchronization (CDC → query database) rather than restoring direct cross-database access.

## Evidence

- `COACTUPC.cbl:3888-4104` (write across ACCTDAT/CUSTDAT)
- `CBTRN02C.cbl:424-559` (write across ACCTDAT/TCATBALF/TRANSACT)
- `COPAUA0C.cbl:448-457` (read across CCXREF/ACCTDAT/CUSTDAT)
