# ADR Index

| ID | Title | Status | Related design documents |
|---|---|---|---|
| ADR-001 | Split into 6 microservices by DDD bounded context | Accepted | `02-bounded-contexts.md`, `03-service-decomposition.md` |
| ADR-002 | Modeling approach for legacy identifiers and amounts | Accepted | `04-data-dictionary`, `07-java-conventions.md` |
| ADR-003 | Data ownership and "one database per service" | Accepted | `03-service-decomposition.md`, `04-context-map.md` |
| ADR-004 | Orchestration ownership for cross-aggregate use cases | Accepted | `03-service-decomposition.md` §4 |
| ADR-005 | Cross-service consistency and idempotency strategy | Accepted | `03-service-decomposition.md` §5 |
| ADR-006 | Concurrency control switched to optimistic locking | Accepted | `07-java-conventions.md` §6 |
| ADR-007 | Scope of mandatory authentication checks (POC) | Accepted (with risks) | `docs/ops/01-docker-runbook.md` §7 |
| ADR-008 | Password storage and login error semantics | Accepted | `02-bounded-contexts.md` §IAM |
| ADR-010 | Separation of responsibilities between the posting engine and the general ledger | Accepted | `11-money-path-slice-a2.md` §2–3 |
| ADR-011 | Read-only reconciliation and difference handling boundaries | Accepted | `12-reconciliation-and-eod.md` §2, §5 |
| ADR-012 | Payment settlement recorded through posting batches | Accepted | `13-payments-billing-limits-fx.md` §2 |
