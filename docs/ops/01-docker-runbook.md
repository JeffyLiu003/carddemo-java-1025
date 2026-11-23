# Docker Runbook and Debugging Guide

## 1. Prerequisites

| Component | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker | 24+ (BuildKit) | `docker version` |
| Docker Compose | v2 | `docker compose version` |

Image note: Docker Hub is unreachable from the current environment, so the `Dockerfile` uses the locally available image
`eclipse-temurin:21.0.8_9-jdk` as the runtime and requires the host to run `mvn package` to produce the jar first.
If Docker Hub is reachable, a multi-stage build (`maven:3.9-eclipse-temurin-21` +
`eclipse-temurin:21-jre`) can be used instead.

## 2. Start / Stop / Reset

```bash
# one command: package + build image + start
./scripts/build-and-up.sh

# step by step
mvn -q -DskipTests package
docker compose build
docker compose up -d
docker compose ps

# view startup logs (the first start takes about 15–25 seconds)
docker compose logs -f transaction-service

# stop (keeping data)
docker compose down

# stop and wipe the databases (Flyway re-runs the seeds)
docker compose down -v

# extension services (P3/P4, 12 of them; only start them all with ≥16GB memory)
docker compose --profile extended up -d
docker compose --profile extended ps
```

Startup order: `postgres` (`pg_isready` health check) → services without dependencies → `transaction` / `authorization` →
`bff` / `search` / `web-ui`. Downstream clients are lazily loaded, so service startup does not depend on downstream availability;
a failed call returns 503.

Open **http://localhost:8090/** for the business UI (the login credentials are pre-filled).

## 3. End-to-End Smoke Test

```bash
./scripts/smoke-test.sh
```

Coverage: the 12 default health probes, login, accounts/customers/cards, holds, posting/general ledger/reconciliation/EOD, payments/statements/limits/FX, BFF/search.
Extension services (loans/risk/operations) require `--profile extended up -d` first; verify them item by item following the pages in `README.md`.

Manual verification commands are in `README.md` §4.

## 4. Debugging Techniques

### 4.1 Logs

```bash
docker compose logs -f account-service
docker compose logs --tail=200 transaction-service | grep -i error
```

### 4.2 Entering the Container / Inspecting Processes and Ports

```bash
docker compose exec account-service sh
docker compose exec account-service ps -ef | grep java
docker compose exec account-service wget -qO- localhost:8084/actuator/health
```

> The runtime image is a full JDK (not slimmed down); when `wget`/`curl` are unavailable, use `sh` + `/dev/tcp` or curl the mapped port from the host directly.

### 4.3 Querying the Database Directly

```bash
docker compose exec postgres psql -U carddemo -d carddemo_account \
  -c "select account_id, current_balance, version from account order by 1;"

docker compose exec postgres psql -U carddemo -d carddemo_authorization \
  -c "select account_id, approved_count, approved_amount, credit_balance from auth_summary;"

docker compose exec postgres psql -U carddemo -d carddemo_transaction \
  -c "select transaction_id, account_id, type_code, amount, processed_timestamp from transaction order by 1 desc limit 10;"
```

### 4.4 Restarting a Single Service (after code changes)

```bash
mvn -q -DskipTests -pl services/account-service -am package
docker compose up -d --build account-service
```

### 4.5 Local IDE Debugging (no packaging)

```bash
docker compose up -d postgres
DB_HOST=localhost DB_NAME=carddemo_account mvn -f services/account-service/pom.xml spring-boot:run
# the same applies to other services; change DB_NAME / SERVER_PORT
```

For cross-service calls set `CARD_SERVICE_URL=http://localhost:8083`,
`ACCOUNT_SERVICE_URL=http://localhost:8084` and `CUSTOMER_SERVICE_URL=http://localhost:8082`.

### 4.6 Web UI and API Console

**Business UI (recommended)**: http://localhost:8090/
- Login credentials are pre-filled: `ADMIN001 / admin123` (administrator), `USER0001 / user1234` (regular user).
- Features: account view, credit card query/maintenance, transaction query/entry, bill payment, transaction reports, authorization management,
  general ledger/reconciliation/EOD, payments/statements, limits, FX, premium cashback, loans, risk, operations, user management (administrator).
- All `/api/**` is same-origin proxied by web-ui to the corresponding microservice (no CORS in the browser).

**API console (Swagger UI)**: each microservice's root path redirects to its own Swagger UI.

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| iam | http://localhost:8081/ | http://localhost:8081/v3/api-docs |
| customer | http://localhost:8082/ | http://localhost:8082/v3/api-docs |
| card | http://localhost:8083/ | http://localhost:8083/v3/api-docs |
| account | http://localhost:8084/ | http://localhost:8084/v3/api-docs |
| transaction | http://localhost:8085/ | http://localhost:8085/v3/api-docs |
| authorization | http://localhost:8086/ | http://localhost:8086/v3/api-docs |
| ledger | http://localhost:8088/ | http://localhost:8088/v3/api-docs |
| reconciliation | http://localhost:8089/ | http://localhost:8089/v3/api-docs |
| payment | http://localhost:8091/ | http://localhost:8091/v3/api-docs |
| billing | http://localhost:8092/ | http://localhost:8092/v3/api-docs |
| limit | http://localhost:8093/ | http://localhost:8093/v3/api-docs |
| fx | http://localhost:8094/ | http://localhost:8094/v3/api-docs |
| loan | http://localhost:8095/ | http://localhost:8095/v3/api-docs |
| fraud | http://localhost:8096/ | http://localhost:8096/v3/api-docs |
| aml | http://localhost:8097/ | http://localhost:8097/v3/api-docs |
| dispute | http://localhost:8098/ | http://localhost:8098/v3/api-docs |
| collections | http://localhost:8099/ | http://localhost:8099/v3/api-docs |
| batch | http://localhost:8100/ | http://localhost:8100/v3/api-docs |
| reporting | http://localhost:8101/ | http://localhost:8101/v3/api-docs |
| notification | http://localhost:8102/ | http://localhost:8102/v3/api-docs |
| document | http://localhost:8103/ | http://localhost:8103/v3/api-docs |
| workflow | http://localhost:8104/ | http://localhost:8104/v3/api-docs |
| audit | http://localhost:8105/ | http://localhost:8105/v3/api-docs |
| reference | http://localhost:8106/ | http://localhost:8106/v3/api-docs |
| bff | http://localhost:8107/ | http://localhost:8107/v3/api-docs |
| search | http://localhost:8108/ | http://localhost:8108/v3/api-docs |

Login: `POST /api/v1/auth/login` → `Try it out` (body pre-filled with `ADMIN001/admin123`) → `Execute`.
To use the regular user instead: change it to `USER0001/user1234`.

New endpoints need no frontend changes: the annotations (`@Operation`, `@Schema`, `@ExampleObject`) are reflected on the page automatically.
If the page shows stale content, force-refresh the browser (Ctrl/Cmd+Shift+R) or retry after rebuilding the service.

### 4.7 Breakpoints and Remote Debugging (optional)

Add the following to each service's `ENTRYPOINT`:
`-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`;
add `ports: ["5005:5005"]` to Compose, and connect from the IDE with Remote JVM Debug.

## 5. Troubleshooting

| Symptom | Cause | Action |
|---|---|---|
| `docker compose build` times out pulling `eclipse-temurin` | Docker Hub unreachable | Use the locally available image (the current Dockerfile already does); or configure a registry mirror |
| A service exits right after startup with `Connection refused` in the log | PostgreSQL not ready | Confirm the `postgres` container is healthy; retry after `docker compose down -v` |
| `FlywayValidateException: checksum mismatch` | An already-applied migration script was modified | In the demo environment, re-initialize with `docker compose down -v`; in production add a new migration version and never edit historical scripts |
| Port already in use | An existing local service | Change the port mapping in `docker-compose.yml`, or free the port (5432/8081–8108/8090) |
| Opening 8080 does not show this UI | Host port 8080 is taken by another program; this project maps to **8090** | Use http://localhost:8090/; to use 8080, free it first and then change the Compose mapping |
| Bill payment returns 409 `NOTHING_TO_PAY` | Account balance ≤ 0 | Use an account with an amount owed (seed: 1/2/3/4/6/7/8) |
| Posting returns a `103` decline | The account expiry date is earlier than the transaction's original date | Seed accounts 1–5 and 7 have expired, so use account 6 (2027-03-09) or 8 (2028-09-30) for the demo |
| Authorization returns `4100` | Available credit = limit − amount owed − pending authorization holds | Expected; account 1 has 80.00 available credit |
| Duplicate transaction ID | The posting `transactionId` already exists | Posting is handled idempotently (counted in posted + duplicates); use a new transaction ID to verify a new posting |
| Insufficient container memory | 16 containers by default (15 JVM + PG); 28 containers with extended | ≥10GB for the default; ≥16GB for all. Measured at 8.3GB: the default 16 are stable; for extensions use ≤6 at a time and stop them after validation |
| A service reports `too many clients already` at startup | services × Hikari 10 > the PostgreSQL default of 100 | Compose already sets `max_connections=300`; if you use your own PG, raise it accordingly |

## 6. Data Reset and Rebuild

```bash
docker compose down -v                 # delete volumes (all business data and Flyway history)
mvn -q -DskipTests package
docker compose up -d --build
```

## 7. Known Security Gaps (must be understood)

1. **Resource services do not enforce token validation**: ports 8081–8108 are equivalent to trusted intranet calls.
   This is for local/isolated-network demos only; never expose them to the internet or use them in production.
2. Before production: connect an API gateway for unified authentication (OAuth2/OIDC), enable mTLS between services, use a secret manager
   (`IAM_TOKEN_SECRET` must not keep its default value), and add audit logging and rate limiting.
3. Seed passwords are for demo only (`ADMIN001/admin123`, `USER0001/user1234`) and must be reset before going live.
4. Following the original semantics, the authorization endpoint only evaluates "limit/status" and does not integrate fraud lists or merchant risk controls (U4).

## 8. Verification Record (this delivery)

| Item | Result |
|---|---|
| `mvn test` (27 modules) | 763 test cases all green |
| `mvn verify` | JaCoCo report; both ArchUnit rules pass |
| `docker compose up -d --build` | 16 default containers started, 15 services health=UP |
| `docker compose --profile extended up -d` | 12 extension services added (29 containers in total) |
| Smoke: login/accounts/customers/cards | Passed |
| Smoke: posting (normal + over-limit 102) | Passed |
| Smoke: authorization (approve 50 / over-limit 4100 / summary hold) | Passed |
| Smoke: bill payment (1940 → balance 0) | Passed |
| Smoke: reports (6 items, total 2613.12) | Passed |
| Smoke: account holds (create/limit reduction/release) | Passed |
| Smoke: posting → general ledger journal entry → trial balance → reversal | Passed |
| Smoke: reconciliation (batches ↔ journal entries) and EOD | Passed |
| Smoke: payment intake → acceptance → settlement → return | Passed |
| Smoke: statement generation/limit check/FX quote | Passed |
| Extension: loan application → approval → disbursement → repayment | Passed |
| Extension: fraud scoring/alerts, AML screening, disputes, collections | Passed |
| Extension: reports, notifications, documents, workflow, audit, reference data | Passed |
| Extension: BFF customer 360 and unified search | Passed |
| Premium cashback: enrollment → spend 1000 → cashback 30 → account credit → idempotency | Passed |
| P6: k6 scripts, chaos scripts, JaCoCo, ArchUnit | Delivered/passed |
| Swagger UI (26 service root paths 302 → `/swagger-ui/index.html`, HTTP 200) | Passed |
| Web UI (8090 home 200, correct static asset MIME types, same-origin proxied login/query/404) | Passed |
| Reconciliation/EOD page syntax and proxy routes (`node --check`, `/api/v1/reconciliation-runs`) | Passed |
| Payments/statements/limits/FX page syntax and proxy routes (`node --check`, four prefix groups) | Passed |
| Frontend script syntax (`node --check app.js`) | Passed |
| UI read-only end-to-end (jsdom drives the real UI: login, menus, accounts/cards/transactions/reports/authorizations/users, role permissions) | 18/18 passed |
| UI write end-to-end (transaction entry, bill payment, card maintenance, user create/delete, fraud flag) | 7/7 passed |
| Payments/statements/limits/FX end-to-end | Passed |
| Login request body pre-fill (`ADMIN001` / `admin123`) | Passed (`/v3/api-docs` already generates the example) |
