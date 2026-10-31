# 02 Security, Performance and Quality Closure (P6)

> Scope: NFR targets, load/chaos testing approaches, security checklist and known gaps for the local Compose demo environment.
> Conclusion: the POC can be demonstrated on an isolated network; before production, authentication, TLS, rate limiting, secret management and audit integration must be completed.

## 1. NFR Targets (demo environment)

| Metric | Target | Verification method |
|---|---|---|
| Query endpoint p95 | < 800ms | `scripts/perf/k6-smoke.js` |
| Write endpoint p95 | < 1200ms | Extended load test scripts |
| Error rate | < 1% | k6 `http_req_failed` |
| Core service availability | Single instance, restarts allowed | `docker compose ps` / health |
| Posting idempotency | Repeated requests do not debit twice | Unit tests + smoke test |
| General ledger consistency | Trial balance `Σdebit=Σcredit` | Reconciliation/EOD |
| Recovery time (local) | < 2 minutes | Restart the service/Compose |

## 2. Performance Testing

```bash
# requires k6: brew install k6
k6 run scripts/perf/k6-smoke.js
# specify the entry point
BASE_URL=http://localhost:8090 k6 run scripts/perf/k6-smoke.js
```

The script is a read-only scenario (accounts, limits, transactions, trial balance, reconciliation history) to avoid polluting accounting data with load tests.
Write-path load tests should run on a separate dataset, with general ledger sync disabled first or degradation accepted.

## 3. Chaos / Degradation

```bash
./scripts/chaos-test.sh
```

Steps:

1. Record the limit of account 6;
2. Stop `ledger-service`;
3. Create a 1.00 posting batch (it should succeed, general ledger sync degrades to WARN and `ledgerEntryId` is empty);
4. Restore the general ledger service and reverse the test batch.

Verification: the funds chain does not fail because the general ledger is unavailable, and the balance returns to normal after recovery.

## 4. Coverage and Architecture Constraints

```bash
mvn verify                 # JaCoCo report: target/site/jacoco/index.html
mvn -pl services/account-service test   # ArchUnit layering constraints
```

- JaCoCo is configured in the parent POM with `prepare-agent` + `report` and has no hard threshold (to avoid blocking in the demo environment).
- The ArchUnit example lives in `services/account-service/src/test/java/com/carddemo/account/ArchitectureTest.java`:
  domain does not depend on application/infrastructure/interfaces; application does not depend on interfaces.
- It is recommended to copy ArchUnit to all services later and set coverage thresholds in CI (core domain ≥ 70%).

## 5. Security Checklist (OWASP Top 10 Summary)

| Risk | Current state | Action |
|---|---|---|
| A01 Broken access control | Resource services do not enforce token validation (ADR-007) | Connect an API gateway/OAuth2 before production; mTLS between services |
| A02 Cryptographic failures | Local HTTP; PII in logs is not fully masked | Site-wide TLS; field-level encryption; log masking |
| A03 Injection | Parameterized JPA; no string-concatenated SQL | Maintain; whitelist external queries |
| A04 Insecure design | No rate limiting/replay protection; payments have no cut-off | Gateway rate limiting; idempotency keys already exist; add cut-off |
| A05 Security misconfiguration | Default passwords/keys for demo only | Secret management; never ship default values |
| A06 Vulnerable and outdated components | Dependencies managed by the Spring Boot BOM | Run dependency scanning in CI (see below) |
| A07 Identification and authentication failures | PBKDF2 login; no MFA | Add MFA/device fingerprinting |
| A08 Software and data integrity failures | Posting/general ledger idempotency and reversal | Maintain; add event signing |
| A09 Security logging and monitoring failures | Structured logs + Actuator | Connect SIEM/alerting; the audit service already exists |
| A10 SSRF | Downstream URLs injected via configuration | Whitelist validation; user-controlled URLs are forbidden |

Dependency scanning recommendation:

```bash
mvn -q dependency:tree > /tmp/deps.txt
# requires network/plugin:
mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7
```

## 6. Known Boundaries

- No API gateway (web-ui same-origin proxy + BFF handle the edge responsibilities);
- No multi-tenant/multi-entity accounting or account-level multi-currency balances;
- No integration with real OpenSearch, MQ or SWIFT/ISO 20022 messages;
- No full-chain load testing environment, chaos platform, SIEM or compliance reporting;
- Extension services are not started by default; use `docker compose --profile extended up -d`.
