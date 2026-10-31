# Documentation Index

## 1. Legacy Knowledge (As-Is)

| Document | Content |
|---|---|
| [legacy/01-codebase-inventory.md](legacy/01-codebase-inventory.md) | 44 programs, copybooks, data stores, JCL, interfaces, risk quantification |
| [legacy/02-five-level-model.md](legacy/02-five-level-model.md) | L1–L5 five-level business model (every fact with evidence), migration impact, BCP, open items |
| [legacy/03-business-rule-catalog.md](legacy/03-business-rule-catalog.md) | Business rule catalog: legacy evidence → target landing point → implementation status |
| [legacy/04-data-dictionary.md](legacy/04-data-dictionary.md) | Field-level data dictionary and enums for the 14 logical entities |
| [legacy/05-ubiquitous-language.md](legacy/05-ubiquitous-language.md) | Ubiquitous language glossary, terminology conflicts and disambiguation |

## 2. Target Design (To-Be)

| Document | Content |
|---|---|
| [design/01-domain-analysis.md](design/01-domain-analysis.md) | Subdomain decomposition, aggregates and invariants, domain services/events, semantic differences |
| [design/02-bounded-contexts.md](design/02-bounded-contexts.md) | Responsibilities, boundaries, and data ownership of the 26 bounded contexts |
| [design/03-service-decomposition.md](design/03-service-decomposition.md) | Service decomposition, invocation matrix, failure semantics, deployment and capacity |
| [design/04-context-map.md](design/04-context-map.md) | Context map, integration contracts, anti-corruption layer |
| [design/05-api-catalog.md](design/05-api-catalog.md) | All REST contracts, seed data contracts, consolidated error code list |
| [design/06-traceability-matrix.md](design/06-traceability-matrix.md) | Legacy program → service → API → rule → test |
| [design/07-java-conventions.md](design/07-java-conventions.md) | Unified baseline for coding/layering/configuration/testing |
| [design/09-scale-up-blueprint.md](design/09-scale-up-blueprint.md) | Scale-up blueprint: current baseline, target service map, seven-phase roadmap |
| [design/10-money-path-slice-a1.md](design/10-money-path-slice-a1.md) | Money path slice A1: account productization, holds, posting, general ledger and reconciliation design |
| [design/11-money-path-slice-a2.md](design/11-money-path-slice-a2.md) | Money path slice A2: posting engine, general ledger, trial balance and front end |
| [design/12-reconciliation-and-eod.md](design/12-reconciliation-and-eod.md) | Money path slice A1.4: reconciliation, exception handling and EOD |
| [design/13-payments-billing-limits-fx.md](design/13-payments-billing-limits-fx.md) | P2: payment, billing, limits and FX |
| [design/14-risk-credit-collections.md](design/14-risk-credit-collections.md) | P3: credit, fraud, AML, disputes, collections, batch processing |
| [design/15-operations-support.md](design/15-operations-support.md) | P4: reports, notifications, documents, workflow, audit, reference data |
| [design/16-channel-and-quality.md](design/16-channel-and-quality.md) | P5/P6: BFF, search, portal, performance, chaos and security |
| [design/adr/README.md](design/adr/README.md) | 11 architecture decision records (MADR) |

## 3. Operations

| Document | Content |
|---|---|
| [ops/01-docker-runbook.md](ops/01-docker-runbook.md) | Build, start, debug, troubleshoot, reset, security gaps |
| [ops/02-security-and-performance.md](ops/02-security-and-performance.md) | NFRs, load testing, chaos, OWASP checklist and security gaps |

## 4. Simulated Requirements (2026-10)

| Document | Content |
|---|---|
| [requirements/README.md](requirements/README.md) | Requirement pack index, priority and scheduling recommendations |
| [requirements/REQ-2026-001-installment.md](requirements/REQ-2026-001-installment.md) | Statement/purchase installment: application, preview, schedule, early settlement |
| [requirements/REQ-2026-002-card-loss.md](requirements/REQ-2026-002-card-loss.md) | Credit card loss reporting and replacement: self-service loss reporting, replacement ticket, progress |
| [requirements/REQ-2026-003-limit-increase.md](requirements/REQ-2026-003-limit-increase.md) | Limit adjustment request and approval: pre-review, approval flow, SoD |
| [requirements/REQ-2026-004-dispute.md](requirements/REQ-2026-004-dispute.md) | Transaction dispute: online request, evidence, provisional credit, chargeback |
