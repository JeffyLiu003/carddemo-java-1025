# Java Coding and Layering Conventions (Unified Baseline for the New Project)

These conventions are the implementation baseline for all microservices in `carddemo-java-1025`. Each service's code must build independently, produce its own image, and use its own database.

## 1. Technology Baseline

| Item | Value |
|---|---|
| JDK | 21 |
| Framework | Spring Boot 3.3.6 (Web / Data JPA / Validation / Actuator) |
| Build | Maven multi-module, parent POM `com.carddemo:carddemo-java:1.0.0-SNAPSHOT` |
| Database | PostgreSQL 16 (Docker) / H2 (unit tests, `local` profile), one database per service |
| Migration | Flyway; scripts live in `src/main/resources/db/migration` |
| Tests | JUnit 5 + AssertJ + MockMvc + H2 |
| Ports | iam 8081 / customer 8082 / card 8083 / account 8084 / transaction 8085 / authorization 8086 / marketing 8087 / ledger 8088 / web-ui 8090 |

## 2. Package Structure and Layering

```
com.carddemo.<context>
├── <Context>Application.java        # application entry point
├── interfaces/rest                  # inbound adapter: controllers, request/response DTOs
├── application                      # application services: use-case orchestration, transaction boundaries
├── domain
│   ├── model                        # aggregate roots, entities, value objects, domain behavior
│   ├── repository                   # repository ports (plain interfaces, framework-agnostic)
│   └── exception                    # domain exceptions (BusinessRuleException / NotFoundException)
└── infrastructure
    ├── persistence                  # Spring Data repository adapters
    ├── client                       # outbound adapters: calls to other microservices
    └── config                       # configuration classes
```

Dependency direction: `interfaces → application → domain ← infrastructure`. The domain does not depend on Spring Web or
on other services; infrastructure implements the ports defined by the domain.

## 3. Naming and Modeling Conventions

- Aggregate roots are named in the ubiquitous language: `Account`, `Card`, `Customer`, `TransactionRecord`, `AuthorizationCase`.
- Domain behavior lives on the entities (rich domain model): `account.applyPosting(amount)`, not piles of setters in services.
- Domain exceptions express only the failure of a business assertion: `throw new BusinessRuleException("CODE", "message")`.
- A failed service-to-service call throws `RemoteServiceException` (`503`) and must not swallow the exception.
- **Legacy system identifiers are always zero-padded strings**: `accountId` 11 digits, `cardNumber` 16 digits,
  `customerId` 9 digits, `transactionId` 16 digits. Never use `long`, which would drop the leading zeros.
- Amounts are always `BigDecimal` with database type `numeric(12,2)`; `double` is forbidden.
- Dates use `LocalDate` (`YYYY-MM-DD`) and timestamps use `LocalDateTime`.
- Status codes keep the legacy values: `Y/N` (account/card status), `A/U` (user type), `00/05` (authorization response code).

## 4. REST Conventions

- Base path `/api/v1/<plural resource>`; internal service-to-service calls use the same API without an `/internal` prefix.
- Request/response DTOs are Java `record`s, with Jakarta Validation annotations on request DTOs.
- List endpoints uniformly return `{"items": [...]}` (paginated endpoints additionally return `page`, `size` and `total`).
- Time fields are ISO-8601 strings; amount fields are numeric (`1940.00`), never strings.
- Error responses are uniform:

```json
{
  "code": "ACCOUNT_NOT_FOUND",
  "message": "Account 00000000001 not found",
  "details": ["activeStatus: Account Status must be Y or N."]
}
```

- HTTP semantics: `404` not found, `409` business rule conflict, `400` parameter validation failure, `503` downstream unavailable.

## 5. Configuration Conventions

All services use the same set of environment variables to simplify Compose orchestration:

| Variable | Description |
|---|---|
| `SERVER_PORT` | HTTP port; defaults in the table above |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | PostgreSQL connection |
| `<TARGET>_SERVICE_URL` | Downstream service base URL, e.g. `ACCOUNT_SERVICE_URL` |
| `SPRING_PROFILES_ACTIVE` | `docker` / `local` / `test` for tests |

`application.yml` contains only defaults plus environment variable placeholders; hostnames outside the container must never be hard-coded.

## 6. Database Conventions

- One independent database per service (`carddemo_iam`, `carddemo_customer`, `carddemo_card`,
  `carddemo_account`, `carddemo_transaction`, `carddemo_authorization`); cross-database JOINs are forbidden.
- Table names are lower_snake_case and field names map one-to-one to legacy copybook fields (see `docs/legacy/04-data-dictionary.md`).
- Every aggregate table carries `version bigint not null default 0` (`@Version` optimistic locking), replacing the legacy
  `READ UPDATE` plus manual concurrency comparison.
- Flyway `V1__<context>_schema.sql` creates tables and `V2__<context>_seed.sql` holds seed data;
  seed data must be idempotent plain `INSERT`s (so H2 and PostgreSQL can both run).

## 7. Test Conventions

- Every service contains at least: unit tests for domain behavior (plain JUnit, no Spring) + one end-to-end case with
  `@SpringBootTest` + `MockMvc` (H2 + Flyway migrations + seed data).
- Assertions focus on business rules and error codes, not on logs or implementation details.
- `mvn -q test` must be fully green; tests must not depend on external networks or other running services.

## 8. Observability and Operations

- Every service exposes `/actuator/health` (including a DB probe), which Docker healthchecks use.
- Logs use the Spring Boot default format (time/level/thread/logger/message) and never print sensitive fields such as card numbers, SSNs or passwords.
- Distributed tracing (OpenTelemetry + B3/W3C propagation) is a later iteration item.
- Every service provides a `Dockerfile` (multi-stage build) and a Compose health check.

## 9. Explicitly Out of Scope for This Phase

- Enforced authentication (tokens are issued in `iam-service`; resource-side validation waits for the gateway, see ADR-0007).
- Distributed transactions: cross-service writes use "local transaction + rollback on failure + idempotency key"; Saga/Outbox is covered in ADR-0005.
- Report layout (the original COBOL print format): only data semantics and filter logic are migrated, with JSON output.
