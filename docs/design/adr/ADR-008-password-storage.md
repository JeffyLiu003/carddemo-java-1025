# ADR-008: Password Storage and Login Error Semantics

| Item | Content |
|----|------|
| ID | ADR-008 |
| Date | 2026-10-25 |
| Status | Accepted |
| Related requirements | BR-SGN-03, BR-USER-03 |

## Context

The legacy system stores `SEC-USR-PWD PIC X(8)` in plaintext and compares it in plaintext (`CSUSR01Y.cpy:21`, `COSGN00C.cbl:223`),
and new users are not upper-cased while login input is upper-cased, so a password containing lowercase letters can never be used to log in (`COUSR01C.cbl:157`).
The new system must not copy this defect, but must keep the business constraint of "8 fixed-length characters, set by an administrator".

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| Option A: PBKDF2-HMAC-SHA256 (built into the JDK) | Random per-user salt, 210000 iterations, format `pbkdf2$iter$salt$hash` | No extra dependencies; resistant to brute force; can migrate smoothly | Verification/encoding must be implemented by hand |
| Option B: BCrypt (spring-security-crypto) | Introduce Spring Security Crypto | Industry standard; simple to implement | Adds a dependency; coupled to the security framework version |
| Option C: keep plaintext + encrypt transport | HTTPS only | Zero migration cost | Serious violation (MLPS/PCI DSS) |

## Quantitative Trade-off Table

| Option | Implementation cost | Performance overhead | Operational agility | Auditability | Risk |
|---|---|---|---|---|---|
| A | Low (about 60 lines) | Medium (about 100–200 ms per login) | Immediate | High (algorithm and iteration count are auditable) | Low |
| B | Low | Medium | Immediate | High | Low |
| C | None | None | Immediate | Low | Extremely high (compliance blocker) |

## Decision

Adopt Option A (PBKDF2-HMAC-SHA256, 210000 iterations, 128-bit salt) with two improvements:

1. User IDs and passwords are compared "as-is" (no more forced upper-casing), eliminating the legacy case defect; user IDs are stored upper-cased.
2. Failed logins uniformly return `INVALID_CREDENTIALS` (without distinguishing "user does not exist" from "wrong password") to prevent user enumeration,
   while the specific reason is still recorded in server-side logs (excluding the password).

## Rationale

1. The JDK ships `SecretKeyFactory("PBKDF2WithHmacSHA256")`, so no security framework needs to be introduced for the demo.
2. An 8-character fixed-length password has limited entropy, so a slow hash + salt is required to raise the attack cost.
3. Uniform error semantics are a security baseline requirement; changes to business prompt wording do not affect the flow.

## Consequences (Impact and Rollback Path)

- Impact: seed user passwords are written to Flyway as hashes; the login API does not return the specific failure reason.
- Rollback: if Spring Security is introduced later, the existing hash can be used as a custom encoder in `DelegatingPasswordEncoder`,
  or passwords can be reset uniformly before go-live.

## Evidence

- `CSUSR01Y.cpy:17-23` (plaintext, 8 characters)
- `COSGN00C.cbl:132-136,221-256`
- `COUSR01C.cbl:136-157`
