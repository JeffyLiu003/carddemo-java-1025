# Documentation Index

## 1. Target Design (To-Be)

| Document | Content |
|---|---|
| [design/03-service-decomposition.md](design/03-service-decomposition.md) | Service decomposition, invocation matrix, failure semantics, deployment and capacity |
| [design/05-api-catalog.md](design/05-api-catalog.md) | All REST contracts, seed data contracts, consolidated error code list |
| [design/09-scale-up-blueprint.md](design/09-scale-up-blueprint.md) | Scale-up blueprint: current baseline, target service map, seven-phase roadmap |
| [design/10-money-path-slice-a1.md](design/10-money-path-slice-a1.md) | Money path slice A1: account productization, holds, posting, general ledger and reconciliation design |
| [design/11-money-path-slice-a2.md](design/11-money-path-slice-a2.md) | Money path slice A2: posting engine, general ledger, trial balance and front end |
| [design/12-reconciliation-and-eod.md](design/12-reconciliation-and-eod.md) | Money path slice A1.4: reconciliation, exception handling and EOD |
| [design/13-payments-billing-limits-fx.md](design/13-payments-billing-limits-fx.md) | P2: payment, billing, limits and FX |
| [design/14-risk-credit-collections.md](design/14-risk-credit-collections.md) | P3: credit, fraud, AML, disputes, collections, batch processing |
| [design/15-operations-support.md](design/15-operations-support.md) | P4: reports, notifications, documents, workflow, audit, reference data |
| [design/16-channel-and-quality.md](design/16-channel-and-quality.md) | P5/P6: BFF, search, portal, performance, chaos and security |
| [design/17-premium-cashback-campaign.md](design/17-premium-cashback-campaign.md) | Premium card cashback campaign: requirements, impact, architecture, review and tests |
| [design/adr/README.md](design/adr/README.md) | 4 architecture decision records (MADR) |

## 2. Operations

| Document | Content |
|---|---|
| [ops/01-docker-runbook.md](ops/01-docker-runbook.md) | Build, start, debug, troubleshoot, reset, security gaps |
| [ops/02-security-and-performance.md](ops/02-security-and-performance.md) | NFR targets, k6 load testing, chaos/degradation and security hardening |

## 3. Simulated Requirements (2026-10)

| Document | Content |
|---|---|
| [requirements/README.md](requirements/README.md) | Requirement pack index, priority and scheduling recommendations |
| [requirements/REQ-2026-001-installment.md](requirements/REQ-2026-001-installment.md) | Statement/purchase installment: application, preview, schedule, early settlement |
| [requirements/REQ-2026-002-card-loss.md](requirements/REQ-2026-002-card-loss.md) | Credit card loss reporting and replacement: self-service loss reporting, replacement ticket, progress |
| [requirements/REQ-2026-003-limit-increase.md](requirements/REQ-2026-003-limit-increase.md) | Limit adjustment request and approval: pre-review, approval flow, SoD |
| [requirements/REQ-2026-004-dispute.md](requirements/REQ-2026-004-dispute.md) | Transaction dispute: online request, evidence, provisional credit, chargeback |
