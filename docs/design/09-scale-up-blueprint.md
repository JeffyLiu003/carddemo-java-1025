# 09 System Scale-Up Blueprint: From 16K Lines to One Million Lines

> Status: analysis draft (not yet in implementation)
> Scope: all services and the frontend of carddemo-java-1025
> Goal: evolve the current "runnable business slice" into a scaled system covering the core capabilities of retail banking

## 1. Executive Summary

1. The current system is **one vertical slice that connects the main flow end to end**: 7 services, 54 endpoints, 36 tables and about 15.7K lines of Java (including tests), averaging 53 lines per file, which is a "thin service" shape.
2. One million lines should not be a direct target. The sensible approach is to **fill in business breadth and depth along a banking capability map**; line count is an outcome, not a KPI. Executing this blueprint yields roughly 950K lines of Java main code plus tests and about 120K lines of frontend and scripts, roughly 1.05M lines in total.
3. Complexity comes mainly from four directions: **product parameterization, processes and exceptions, regulatory compliance, and channels and integration**. Simply adding CRUD endpoints cannot produce maintainable scale.
4. Seven phases are recommended: platform foundation → deepening the existing 7 services → funds and accounting → credit and risk → operations and reporting → channels and multi-entity → scale-up wrap-up.
5. For the first batch, start with the money path of "account + transaction + payment" (see section 13); it pulls in accounting, limits, reconciliation, batch and reporting at the same time and has the highest reuse.

### Target Scale at a Glance

| Metric | Current | Target | Multiple |
|---|---:|---:|---:|
| Java files | 298 | ~9,000 | 30x |
| Java lines (main+test) | 15,717 | ~950,000 | 60x |
| Frontend/script/SQL lines | ~3,700 | ~120,000 | 32x |
| REST endpoints | 54 | ~1,000 | 18x |
| Database tables | 36 | ~580 | 16x |
| Services/deployment units | 7 + web-ui | ~26 + 3 frontends | 4x |
| Test cases | 197 | ~8,000 | 40x |

## 2. Current Baseline

| Module | Java files | Java lines | Endpoints | Notes |
|---|---:|---:|---:|---|
| iam-service | 36 | 1,601 | 7 | Login, user CRUD |
| customer-service | 20 | 1,551 | 4 | Customer CRUD |
| card-service | 23 | 1,303 | 5 | Card CRUD, lookup by account |
| account-service | 22 | 1,050 | 7 | Accounts, posting, repayment, interest accrual, products and holds |
| transaction-service | 93 | 4,189 | 10 | Transaction query, posting, interest run, bill payment |
| authorization-service | 46 | 2,660 | 6 | Authorization decisions, limits, velocity |
| web-ui | 4 | 241 | - | Static pages + reverse proxy |
| **Total** | **298** | **15,717** | **54** | 36 tables, 197 tests |

> The table above is a snapshot taken during the scale-up analysis. After A1.1–A1.4, P2 and P3–P5 landed: 48,396 lines of Java / 903 files / 763 tests
> across the repository, 26 business services + web-ui; 16 containers by default, with `--profile extended` adding 12 extension services.

Supporting facts:

- Code density is 53 lines per file, whereas a typical enterprise Java service has 100–150 lines per file, showing that many current files are just "a shell".
- Domain rules are concentrated in a few aggregates; structures essential to banking systems such as a product catalog, rates, limits, accounting entries, batch processing and reconciliation are missing.
- There is already a decent engineering foundation: DDD layering, Flyway, idempotency keys, uniform error codes, Docker Compose and 197 tests; these should be kept and strengthened.

## 3. Measurement Definitions and Principles

### 3.1 Definitions

- LOC counts Java code in `src/main` + `src/test`, excluding generated code, dependencies and build artifacts.
- Supporting metrics: number of business rules, aggregates, domain events, endpoints, tables, test cases and end-to-end scenarios.
- Every capability must trace back to a requirement/rule ID (continuing the existing `MK-ELI-*` style) to avoid "writing code for line count".

### 3.2 Principles

1. **Business-driven**: every line of code corresponds to a product parameter, rule, state transition, exception path or integration contract.
2. **Vertical slice**: each increment delivers the domain model + API + migration + tests + documentation + (when needed) UI at the same time.
3. **Runnable**: at the end of any phase `docker compose up` works, all tests are green and the demo script runs through.
4. **No over-decomposition**: service boundaries follow bounded contexts, not tables; shared capabilities settle into libraries or platform services.
5. **Quality gates**: ArchUnit layering checks, contract tests, coverage thresholds and static analysis, built up alongside the scale.

## 4. The Four Sources of Complexity

### 4.1 Product Dimension

The scale of a banking system comes first from "product parameterization":

- Accounts: current, savings, time deposit, money market, multi-currency sub-accounts; overdraft limits and tiered rates; minimum balance, monthly fee, free transaction count.
- Cards: debit/credit/prepaid, co-branded, virtual, supplementary; limits, cash advance limits, merchant category restrictions, regional restrictions.
- Loans: personal, auto, mortgage, revolving credit; rate types, repayment methods (equal installment/equal principal/balloon), penalty interest, early repayment.
- Deposits: term products, rate tiers, automatic renewal, early withdrawal penalties.

Each product is a set of configurable parameters + rules + lifecycle, not an enum value.

### 4.2 Process Dimension

- Lifecycle state machines: application → approval → activation → hold → account closure; card issuance → activation → loss reporting → replacement → card closure.
- Exception paths: reversal, return, partial success, unknown timeout, duplicate submission, cross-day cutoff.
- Manual intervention: maker-checker, adjustments, waivers, dispute evidence, collection promises.
- Batch: interest accrual, fee charging, statements, repayment reminders, delinquency roll, general ledger transfer, reconciliation.
- Scheduling: EOD calendar, dependency graph, restart from checkpoint, idempotent re-runs.

### 4.3 Regulatory Dimension

- KYC/CIP: identity verification, document expiry, risk rating, beneficial owners.
- AML/sanctions: list screening, transaction monitoring scenarios, alert handling, SAR filing.
- Consumer protection: dispute handling deadlines, statement disclosure, fee transparency, complaint audit trail.
- Data: retention periods, tamper-proof audit, PII masking, cross-border data restrictions.
- Reporting: regulatory submissions, general ledger trial balance, risk exposure, liquidity metrics.

### 4.4 Channels and Integration

- Payments: ISO 20022 (pain.001/pacs.008/pacs.002), SWIFT MT103/MT202, SEPA, ACH/NACHA, RTP/FedNow, card network settlement.
- Files: bulk disbursement, reconciliation files, receipts, SFTP send/receive with format validation.
- Open banking: OAuth2/OIDC, consent management, account information and payment initiation.
- Internal: event bus, synchronous queries, batch processing, reporting extracts.

## 5. Target Domain Map

| Layer | Subdomain | Hosting services |
|---|---|---|
| Core | Accounts and deposits | account-service |
| Core | Cards and authorization | card-service, authorization-service |
| Core | Payments and transfers | payment-service, transaction-service |
| Core | Credit | loan-service, credit-service (can be merged) |
| Core | Accounting and general ledger | ledger-service |
| Core | Statements and billing | billing-service |
| Core | Risk and anti-fraud | fraud-service, aml-service |
| Supporting | Customer and KYC | customer-service |
| Supporting | Reconciliation and operations | reconciliation-service, batch-service, reporting-service |
| Supporting | Disputes and collections | dispute-service, collections-service |
| Supporting | Notifications and documents | notification-service, document-service |
| Supporting | Reporting and analytics | reporting-service |
| Supporting | Batch processing and EOD | batch-service |
| Supporting | Workflow and rules | workflow-service, rules-service |
| Supporting | Limits, pricing, reconciliation | limit-service, reference-data-service, reconciliation-service |
| Generic | Identity and access | iam-service |
| Generic | Access and BFF | api-gateway, web-admin, customer-portal |
| Generic | Audit and search | audit-service, search (OpenSearch) |

## 6. Target Service Inventory and Size Estimates

> LOC are target values (main+test) covering domain, application, infrastructure, interfaces and tests; generated code is excluded.

| Service | Current LOC | Target capability increments | Target LOC | Endpoints | Tables |
|---|---:|---|---:|---:|---:|
| iam-service | 1,601 | OAuth2/OIDC, MFA, RBAC/ABAC, sessions and devices, password policy, API clients | 25,000 | 25 | 20 |
| customer-service | 1,551 | CIF, KYC/CIP, documents, addresses, related parties, consents, segmentation, risk rating | 40,000 | 45 | 30 |
| card-service | 1,303 | Product catalog, issuance/embossing, PIN, lifecycle, card controls, limits, tokenization, renewal/replacement | 50,000 | 55 | 35 |
| account-service | 1,050 | Product parameters, overdraft, holds/reservations, interest, fees, statements, account closure, multi-currency | 60,000 | 60 | 40 |
| transaction-service | 4,189 | 30+ transaction types, posting engine, transfers, standing orders, bill pay, reversal, scheduled jobs | 70,000 | 70 | 45 |
| authorization-service | 2,660 | Decision pipeline, velocity, limits, fraud scoring, 3DS, partial approval, stand-in | 45,000 | 35 | 25 |
| reconciliation-service | 1,800 | Reconciliation/EOD, discrepancy handling, compensation | 45,000 | 35 | 30 |
| payment-service | New | ACH/SEPA/wire/RTP, ISO 20022, SWIFT, cut-off times, returns, screening | 80,000 | 80 | 50 |
| ledger-service | New | Chart of accounts, journal entries, posting rules, period closing, trial balance, sub-ledger reconciliation | 45,000 | 35 | 30 |
| billing-service | New | Billing cycles, rates/APR, minimum payment, fees, statement generation, grace period, late fees | 45,000 | 40 | 30 |
| loan-service | New | Application, scoring, limits, amortization schedule, disbursement, repayment, early repayment, delinquency handover | 60,000 | 55 | 40 |
| fraud-service | New | Rules/scoring, velocity, device fingerprinting, case management, alerts | 35,000 | 30 | 20 |
| aml-service | New | Sanctions/PEP screening, monitoring scenarios, alerts, SAR workflow | 30,000 | 30 | 20 |
| dispute-service | New | Dispute reason codes, chargeback lifecycle, provisional credit, evidence, arbitration | 30,000 | 35 | 20 |
| collections-service | New | Delinquency bucketing, collections strategy, PTP, repayment plans, waivers, hardship | 30,000 | 30 | 20 |
| notification-service | New | Templates, email/SMS/push, preferences, retries, delivery receipts | 20,000 | 25 | 12 |
| document-service | New | Statement PDFs, contracts, e-signature, archiving, retention policies | 20,000 | 20 | 12 |
| reporting-service | New | Regulatory reports, business dashboards, extracts, data marts, exports | 50,000 | 40 | 25 |
| batch-service | New | Job graphs, EOD calendar, dependencies, checkpoints, re-runs, file transfer | 35,000 | 20 | 15 |
| workflow-service | New | Workflow definitions, tasks, approvals, SLA, escalation, maker-checker | 25,000 | 30 | 15 |
| rules-service | New | Decision tables, DSL, versions, canary release, simulation | 25,000 | 25 | 12 |
| limit-service | New | Customer/account/channel/product limits, usage counters, temporary limit increase | 15,000 | 20 | 8 |
| reference-data-service | New | Currencies, countries, holidays, FX rates, product catalog, rate tables | 15,000 | 25 | 10 |
| reconciliation-service | New | General ledger/sub-ledger, network settlement, discrepancy management, adjustments | 25,000 | 20 | 12 |
| audit-service | New | Tamper-proof logs, search, retention, export | 15,000 | 15 | 6 |
| api-gateway / BFF | New | Routing, authentication, rate limiting, aggregation, channel adaptation | 30,000 | 50 | 5 |
| web-admin / customer-portal | 241 | Operations back office, customer portal, mobile API, component library | 120,000* | - | - |
| **Total** | **15,717** | | **~955,000** | **~965** | **~582** |

\* The 120K frontend lines are JS/TS/HTML/CSS and are not counted in the Java lines.

## 7. Platform Capabilities (Cross-Cutting)

Scale-up presupposes cross-cutting reuse; otherwise 26 services become 26 duplicate implementations.

### 7.1 Events and Consistency

- Kafka or RabbitMQ as the event bus; each service has an outbox table that writes business data + events in the same transaction.
- Consumer idempotency tables, DLQ, replay tooling, event schema versioning.
- Saga orchestration (payments, transfers, disputes) with compensating actions; synchronous calls are used only for queries.

### 7.2 Access and Security

- API Gateway: routing, OAuth2 resource server, rate limiting, quotas, request signing, channel identification.
- Field-level encryption (PAN, document numbers), log masking, PII access auditing.
- Maker-checker, segregation of duties, operation audit trail.

### 7.3 Rules and Workflow

- Rules service: decision tables + DSL with versioning, canary release and simulation; shared by authorization, eligibility, limits and rates.
- Workflow service: approval flows, task inbox, SLA and escalation; reused for limit changes, refunds, disputes and account closure.

### 7.4 Batch Processing and Files

- Spring Batch + scheduler; job graphs, dependencies, checkpoints, idempotent re-runs, failure alerts.
- EOD calendar (holiday table), cut-off times, cross-day cutoff.
- SFTP file gateway: inbound validation, format conversion, acknowledgements, archiving.

### 7.5 Reporting and Data

- Each service writes a read model; reporting-service aggregates; ClickHouse/a data warehouse is introduced when needed.
- Templated regulatory reports; business dashboards; data export with permission control.
- Data retention, partitioning and archiving policies.

### 7.6 Observability and Operations

- Micrometer + Prometheus + Grafana; OpenTelemetry traces; structured logs with traceId.
- Health checks, readiness probes, circuit breakers, timeouts, retry budgets.
- One runbook per service; threshold alerts for key metrics.

### 7.7 Search and Caching

- OpenSearch: full-text search over customers, transactions and disputes.
- Redis: velocity counters, sessions, limit usage, distributed locks.

## 8. Data Scale

| Item | Current | Target |
|---|---:|---:|
| Database instances | 7 | ~22 |
| Tables | 36 | ~580 |
| Flyway migration scripts | ~10 | ~250 |
| Read models/materialized views | 0 | ~40 |
| Demo data | A small set of seeds | 10K customers / 50K accounts / 1M transactions |

The demo data generator should be a first-class citizen: it supports UI demos as well as reporting, performance testing and batch job validation.

## 9. Engineering Scale

- 30+ Maven modules; shared libraries: `common-domain`, `common-events`, `common-security`, `common-observability`, `test-support`.
- OpenAPI-first: contracts generate DTOs and clients to reduce hand-written mapping; MapStruct handles aggregate ↔ DTO.
- Test pyramid: ~6,000 unit, ~1,500 integration (Testcontainers), ~300 contract, ~100 end-to-end, ~30 performance scenarios.
- ArchUnit validates layering and dependency direction; Checkstyle/SpotBugs/PMD; coverage thresholds set per module.
- Documentation: C4 models, ADRs (target 30+), API catalog, event catalog, data dictionary, per-service runbook.
- CI: parallel builds, dependency caching, layered images, change-set based test selection.

## 10. Phased Roadmap

| Phase | Theme | Cumulative LOC | Endpoints | Tables | Key deliverables |
|---|---|---:|---:|---:|---|
| P0 | Platform foundation and engineering skeleton | 25k | 60 | 40 | Shared libraries, event bus + outbox, gateway skeleton, tests/contracts/ArchUnit, data generator |
| P1 | Deepen the existing 7 services | 110k | 180 | 90 | Product parameters, account overdraft/holds/fees, transaction types and reversal, authorization rules, card lifecycle, KYC, RBAC/MFA |
| P2 | Funds and accounting | 250k | 380 | 200 | payment, ledger, billing, limit, FX, reconciliation; ISO 20022 and batch files |
| P3 | Credit and risk | 400k | 560 | 300 | loan, credit, fraud, aml, dispute, collections |
| P4 | Operations and reporting | 560k | 720 | 400 | batch/EOD, reporting, document, notification, workflow, audit, reference-data |
| P5 | Channels and multi-entity | 780k | 880 | 500 | gateway/BFF, back office, customer portal, search, multi-currency/multi-entity, performance tuning |
| P6 | Scale-up wrap-up | 1.0M | 1,000 | 580 | Regulatory reporting, resilience and chaos, full-chain load testing, documentation and training, coverage closure |

### Phase Exit Criteria

- P0: any service can be generated from a template; event publishing/consumption has integration tests; `mvn verify` is fully green.
- P1: all existing UI is migrated to the new model; core rules have IDs and tests; no service connects directly to another's database.
- P2: a single interbank transfer completes the full chain "intake → screening → posting → general ledger → reconciliation → receipt".
- P3: high-risk transactions trigger fraud alerts; delinquent accounts enter collections; disputes complete the chargeback lifecycle.
- P4: EOD runs interest accrual, statements, fee charging and general ledger transfer with one command; reports can be exported.
- P5: two channels (back office/portal) share the BFF; multi-currency accounts can be opened, posted and converted.
- P6: core-scenario load tests meet targets; fault injection is recoverable; audit is traceable.

## 11. Effort and Cadence

- Industry experience: enterprise Java including tests and documentation is about 8,000–15,000 lines per person-month; 1M lines is roughly 70–120 person-months.
- AI assistance can significantly compress coding time, but review, integration, data and testing still require investment; advancing one vertical slice every 2 weeks gives about 4–8 slices per phase.
- Schedule by "capability" rather than "line count": each slice delivers a demonstrable business scenario + tests + documentation.
- Watch build time in parallel: modularization + parallel builds + test sharding, so `mvn test` does not become unusable past 30 modules.

## 12. Risks and Anti-Patterns

1. **Writing code for line count**: copy-paste CRUD, dead code, DTO conversions with no business meaning — they inflate the count short-term and are unmaintainable long-term.
2. **Distributed monolith**: tight coupling between services, shared databases, overly long synchronous call chains.
3. **Missing consistency design**: cross-service writes without outbox/saga leave accounting and business state inconsistent.
4. **Compliance bolted on later**: retrofitting audit, retention, masking and maker-checker is extremely expensive.
5. **Test debt**: writing only the happy path makes regression uncontrollable once the scale grows.
6. **Over-decomposition**: one service per table explodes operations and integration costs.
7. **Unrealistic demo data**: too little data hides reporting, performance and batch problems.
8. **Stale documentation**: without catalogs of services, events, tables and interfaces, maintenance breaks down when people change.

## 13. Recommended First Slices

### Option A: Money Path (recommended)

Account productization → transaction posting engine → payment intake/settlement → general ledger → reconciliation.

- Pulls in: account, transaction, payment, ledger, limit, reference-data, reconciliation, batch.
- Value: a demonstrable end-to-end interbank transfer chain with the broadest reuse.

### Option B: Real-Time Risk

Card lifecycle → authorization decision pipeline → limits/velocity → fraud scoring → 3DS.

- Pulls in: card, authorization, fraud, limit, rules, notification.
- Value: real-time decisioning scenarios with high technical challenge but little dependency on payments and accounting.

### Option C: Compliance and Customer

KYC/CIP → sanctions screening → transaction monitoring → disputes/collections.

- Pulls in: customer, aml, dispute, collections, workflow, document.
- Value: a complete regulatory story, but it lacks money flow and demos less well.

Recommended order: A → B → C — connect the money path and accounting first, then real-time risk, and finally compliance operations.

## 14. Next Steps

1. Confirm the first slices and their priorities.
2. Produce the detailed P0/P1 design: service inventory, aggregates, events, tables, APIs, acceptance scenarios.
3. Implement by vertical slice, keeping tests fully green, Compose runnable and documentation in sync for every slice.
