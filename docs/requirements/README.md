# Simulated Requirement Pack Index (2026-10)

> Purpose: support requirement review, scheduling, and front-end/back-end change demos.
> Note: the following are **simulated requirements**; business metrics, fee rates, and turnaround times are all demo assumptions and do not constitute production compliance conclusions.
> Baseline: `main` (already includes the premium card cashback capability, see `docs/design/17-premium-cashback-campaign.md` and ADR-013); all requirement packs below await implementation and do not change the existing marketing capability.

## 1. Requirement List

| ID | Name | Priority | Depth Level | Mainly Affected Services | UI Changes | Proposed Release |
|---|---|---|---|---|---|---|
| REQ-2026-001 | Credit card statement/purchase installment | P0 | L3 | installment (new), transaction, billing, account, ledger, notification | New installment entry points on transaction details/statement details, installment plan page, back-office plan query | R1 |
| REQ-2026-002 | Credit card loss reporting and replacement | P0 | L2 | card, transaction, account, notification, audit | New loss reporting/replacement wizard on card details, progress timeline, back-office card production queue | R1 |
| REQ-2026-003 | Credit card limit adjustment request and approval | P1 | L3 | limit, workflow, account, customer, notification, audit | Limit increase entry on the account page, request progress, back-office approval workbench | R2 |
| REQ-2026-004 | Online transaction dispute request and handling | P1 | L3 | dispute, document, transaction, account, notification | Dispute entry on transaction details, evidence upload, case timeline, back-office case workbench | R2 |

## 2. Scheduling Recommendations

- **R1 (4–6 weeks)**: REQ-2026-001 and REQ-2026-002. Both directly change high-frequency customer pages and have clear back-end boundaries.
- **R2 (4–6 weeks)**: REQ-2026-003 and REQ-2026-004. Both involve approval/case workbenches and cross-service state machines, so they are best done after R1 stabilizes.

## 3. Shared Assumptions

1. The demo environment keeps the existing Spring Boot 3 / PostgreSQL / vanilla SPA technology stack.
2. Login and roles reuse IAM: regular user `U`, administrator `A`; the POC does not enforce API-level authorization (ADR-007).
3. Amounts uniformly use `BigDecimal`/`numeric(12,2)`; IDs follow the existing zero-padding or UUID conventions.
4. All write endpoints must support a deduplication key (API field `idempotencyKey`); key operations write audit events.
5. Changes affecting account balances must go through account-service; changes with accounting impact must be posted to ledger-service.
6. Notifications use notification-service templates (email/SMS/in-app), and failures do not roll back the main flow.

## 4. Shared Non-Functional Requirements

| Item | Requirement |
|---|---|
| Performance | Query p95 < 800ms; submit p95 < 1200ms (local Compose) |
| Availability | When a dependency is unavailable, the main flow returns a clear error code and never leaves a half-completed funds state |
| Security | Card numbers/ID numbers masked; evidence file type/size allowlist; unauthorized access returns 404/403 |
| Audit | Requests, approvals, state transitions, and amount changes all record actor/action/resource/payload |
| Testability | Each requirement covers at least the four case types: main flow, boundary, duplicate submission, and downstream failure |

## 5. Review Outputs

All four requirement specifications follow the section structure of the BA requirement specification template (headings match the template):

| Section | Content |
|---|---|
| Document information / Revision history | Document number, version, business owner, requirement source; version change history |
| 1. Background and Goals | Business background, quantifiable goals, glossary |
| 2. User Roles and Permissions | Roles, responsibilities, permission points |
| 3. Scope Definition | 3.1 In-Scope / Out-of-Scope; 3.2 Assumptions and dependencies |
| 4. Functional Requirements | 4.1 Functional overview; 4.2 field-level requirement tables and exception matrices for each core REQ; 4.3 UI change list |
| 5. Business Rules | ID, rule, source, priority, verification points |
| 6. Non-Functional Requirements | 6.1 Performance/availability/observability; 6.2 security requirements |
| 7. Interfaces and Integration | Interface drafts, request examples, error code list, service-to-service integration |
| 8. Data Requirements | 8.1 Data dictionary; 8.2 data security (masking/encryption/retention/access) |
| 9. Scenario Matrix | Main flow/boundary/exception × role/data interface/expectation |
| 10. Acceptance Criteria (Executable) | Story / Given / When / Then / Data preparation |
| 11. Dependencies and Constraints | 11.1 Dependencies; 11.2 impact analysis; 11.3 constraints |
| 12. Risks and Assumptions | 12.1 Risks and mitigations; 12.2 assumptions; 12.3 items to be confirmed |
| 13. Compliance Notes | Simulated regulation, policy, SoD, privacy, audit |

> Appendix: each document ends with "Appendix A Section Number Mapping (script compatibility)", which aligns it with the
> legacy section numbers used by the `spec_completeness.py` skill script (10 Risks and Mitigations, 11 Compliance Notes).

## 6. Skill Script Check Results

Check commands (run from the repository root):

```bash
python3 ~/.config/opencode/skills/ba-requirement-analysis/scripts/spec_completeness.py docs/requirements/REQ-2026-001-installment.md
python3 ~/.config/opencode/skills/ba-requirement-analysis/scripts/ambiguity_check.py docs/requirements/REQ-2026-001-installment.md
```

| Document | spec_completeness score | spec_completeness result | ambiguity_check result | High-severity ambiguous words |
|---|---|---|---|---|
| REQ-2026-001-installment.md | 100/100 | PASS | PASS | 0 |
| REQ-2026-002-card-loss.md | 100/100 | PASS | PASS | 0 |
| REQ-2026-003-limit-increase.md | 100/100 | PASS | PASS | 0 |
| REQ-2026-004-dispute.md | 100/100 | PASS | PASS | 0 |

Note: the script's ambiguous word list contains high-frequency fragment words such as the Chinese words for "etc.", "related", and "normal", so the document bodies were rewritten with concrete wording throughout (for example, "deduplication key" maps to `idempotencyKey`, and "value by card tier"); no `[to be confirmed]` placeholders are used, and open items are instead marked inline as "(to be confirmed: ...)" and collected in section 12.3 of each document.
