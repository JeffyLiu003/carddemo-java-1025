# 16 Channel and Experience Slice P5 + Quality Wrap-Up P6

> Upstream: `09-scale-up-blueprint.md` P5/P6
> Status: implemented (P5/P6, boundaries in §6)

## 1. P5 Scope

| Service/Page | Port | Database | Capability |
|---|---|---|---|
| bff-service | 8107 | — (stateless) | Customer 360 aggregation (accounts/cards/customer/transactions/benefits) |
| search-service | 8108 | — (stateless) | Unified customer/transaction search |
| web-ui `#/portal` | — | — | Customer 360 view (calls the BFF) |
| web-ui `#/lending` | — | — | Loan application/approval/disbursement/repayment |
| web-ui `#/risk` | — | — | Fraud/AML/disputes/collections |
| web-ui `#/operations` | — | — | Batch/reports/notifications/documents/workflow/audit/reference data |

Multi-currency: in this phase, pricing is provided by fx-service (quotes/conversion) + reference-data (currencies);
account-level multi-currency balances and multi-entity accounting are deferred (see §6).

## 2. BFF Aggregation

### 2.1 API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/bff/customer-overview?accountId=00000000001` | Customer 360 aggregation |

Response:

```json
{"accountId":"00000000001",
 "position":{"productCode":"CLASSIC","creditLimit":2020.00,"currentBalance":1940.00,
             "activeHoldAmount":0.00,"availableCredit":80.00},
 "cards":[{"cardNumber":"0500024453765740","customerId":"000000001","activeStatus":"Y"}],
 "customer":{"customerId":"000000001","firstName":"Immanuel","lastName":"Kessler"},
 "recentTransactions":[{"transactionId":"...","amount":50.47,"description":"..."}],
 "degraded":[]}
```

Aggregation rules:

- Account `/credit-position` failure → 404 (account does not exist) or 503;
- Card/Customer/Transaction failure → the corresponding node is empty and the service name is recorded in `degraded`; the overall 200 is unaffected;
- Timeout 2s; the customer is resolved from the first card's customerId.

## 3. search-service

### 3.1 API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/search?q=&type=ALL&limit=10` | Unified search |

Rules:

- `type` ∈ ALL/CUSTOMER/TRANSACTION.
- Customer: calls customer `/api/v1/customers?page=0&size=200` and filters locally by customerId/name contains.
- Transaction: 11 digits → accountId; 16 digits → cardNumber or idPrefix; anything else does not query transactions.
- Returns `{items:[{type,id,title,subtitle,amount,reference}], total, degraded}`.
- Downstream failure → that type is empty + degraded and no 5xx is returned (unless everything fails and the query type is explicit).

## 4. Frontend Pages

- `#/portal`: enter an account number, call the BFF to display credit position, cards, customer, recent transactions and benefits; degraded hint.
- `#/lending`: create/list loan applications; approve/reject/disburse; loan details, repayment schedule, repayment.
- `#/risk`: four tabs (fraud, AML, disputes, collections), each with its own list/create/disposition.
- `#/operations`: seven tabs (batch, reports, notifications, documents, workflow, audit, reference data), each with its own list/execute/disposition.
- The proxy gains `/api/v1/bff`, `/api/v1/search`, `/api/v1/loan-applications`, `/api/v1/loans`,
  `/api/v1/fraud-evaluations`, `/api/v1/fraud-alerts`, `/api/v1/screenings`, `/api/v1/aml-alerts`,
  `/api/v1/disputes`, `/api/v1/delinquency-cases`, `/api/v1/jobs`, `/api/v1/job-runs`,
  `/api/v1/reports`, `/api/v1/report-runs`, `/api/v1/notification-templates`, `/api/v1/notifications`,
  `/api/v1/documents`, `/api/v1/workflow-definitions`, `/api/v1/workflow-tasks`,
  `/api/v1/audit-events`, `/api/v1/reference`.

## 5. P6 Deliverables

1. **Performance**: `scripts/perf/k6-smoke.js`, covering login, accounts, posting and reconciliation queries; thresholds p95 < 800ms, error rate < 1%.
2. **Chaos/graceful degradation**: `scripts/chaos-test.sh` stops ledger and verifies that posting still succeeds with an empty `ledgerEntryId`; a reversal works after recovery.
3. **Security**: `docs/ops/02-security-and-performance.md` records the OWASP checklist, dependency scanning commands and NFR metrics.
4. **Coverage**: JaCoCo is added to the parent POM (`mvn verify` generates the report, with no hard gate to avoid blocking the demo environment).
5. **Architecture constraints**: ArchUnit tests are added in account-service, verifying that domain does not depend on infrastructure/interfaces.
6. **Documentation**: README service table/page table/test count/container count; the runbook gains the extended profile, performance and chaos commands.

## 6. Explicitly Not Implemented (P5/P6 Boundaries)

- Account-level multi-currency balances, multi-entity/multi-tenant accounting;
- A real OpenSearch/ES index (search is real-time cross-service filtering);
- An API gateway (the web-ui same-origin proxy takes on the edge gateway role and the BFF handles aggregation);
- A standalone customer portal/mobile app (presented as `#/portal` inside web-ui);
- A full-chain load-testing environment, chaos platform and SIEM integration.

## 7. Acceptance

1. BFF aggregation returns accounts/cards/customer/transactions/benefits; a single downstream failure only degrades rather than failing the whole response.
2. Search returns results by customer/transaction type; an invalid type returns 400.
3. The four new pages support the core flows; navigation/proxy are correct.
4. The k6 script runs; the chaos script can stop/restore ledger.
5. `mvn verify` generates the JaCoCo report; ArchUnit passes.
6. Default Compose has 17 containers (15 core + BFF + search); `--profile extended` adds 12 extension services.
