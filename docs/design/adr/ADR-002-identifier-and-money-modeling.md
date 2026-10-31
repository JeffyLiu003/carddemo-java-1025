# ADR-002: Modeling Approach for Legacy Identifiers and Amounts

| Item | Content |
|----|------|
| ID | ADR-002 |
| Date | 2026-10-25 |
| Status | Accepted |
| Related requirements | Data dictionary `docs/legacy/04-data-dictionary.md` |

## Context

Business identifiers in the legacy system are fixed-length numeric fields **with leading zeros**: `ACCT-ID PIC 9(11)`, `CUST-ID PIC 9(9)`,
`CARD-NUM X(16)`, `TRAN-ID X(16)`. Amounts are `S9(10)V99` (fixed point with two decimals). Mapping them with `long`/`double`
would lose the leading zeros of `00000000001` and introduce binary floating-point errors.

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| Option A: zero-padded string + BigDecimal | Identifiers as `String` (DB `VARCHAR(11/9/16)`), amounts as `BigDecimal` + `NUMERIC(12,2)` | One-to-one mapping with legacy data; no precision loss; strings can be compared and used directly in URL paths | Slightly larger index footprint; format must be validated at the entry point |
| Option B: numeric type + zero padding at the presentation layer | `long` + front-end formatting | Compact database indexes; fast numeric comparison | Leading zeros are lost in APIs, logs, and cross-service calls, which easily breaks reconciliation with the legacy system |
| Option C: UUID primary key + legacy number as business key | Surrogate UUID key | Decoupled from the legacy system | Adds mapping cost; requires a second lookup during reconciliation and troubleshooting; low benefit for a demo project |

## Quantitative Trade-off Table

| Option | Implementation cost | Performance overhead | Operational agility | Auditability | Risk |
|---|---|---|---|---|---|
| A | Low | Low (16-byte key, index difference is negligible) | Immediate | High (field-by-field reconciliation with legacy is possible) | Low |
| B | Low | Low | Immediate | Low (reconciliation requires zero-padding conversion) | Medium-high (leading zeros lost) |
| C | Medium | Medium (one extra mapping lookup) | Immediate | Medium | Medium |

## Decision

Adopt Option A. All business identifiers are zero-padded, fixed-length strings in the domain model, DTOs, database, and URLs;
amounts uniformly use `BigDecimal` (DB `NUMERIC(12,2)`), and no `double` arithmetic is performed inside services.

## Rationale

1. The parallel-run period requires item-by-item reconciliation by business number (BR-POST/BR-AUTH); losing leading zeros would directly cause reconciliation failures.
2. A 16-digit card number exceeds the `int` range and is non-numeric by nature in the legacy system (historical data may contain non-digit characters).
3. Interest calculation `TRAN-CAT-BAL × rate ÷ 1200` must use fixed-point rounding (`HALF_UP`, scale 2); `double` is unacceptable.

## Consequences (Impact and Rollback Path)

- Impact: all query parameters are strings; `@Pattern` validation is applied uniformly at the entry point (11/9/16 digits).
- Rollback: if a numeric surrogate key is needed later, add `id BIGSERIAL` while keeping the unique constraint on the business key; the API stays unchanged.

## Evidence

- `CVACT01Y.cpy:5`, `CVCUS01Y.cpy:5`, `CVACT02Y.cpy:5`, `CVTRA05Y.cpy:5`
- `docs/legacy/04-data-dictionary.md` identifier rules
