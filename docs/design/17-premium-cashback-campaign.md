# 17 Premium Credit Card Term Cashback Campaign Capability

> Status: design complete, moving into implementation
> Reference: the removed legacy marketing module (08/ADR-009); this design re-scopes it into a narrow "premium card + term campaign + purchase cashback" capability

## 1. Requirements and Acceptance Criteria

### 1.1 Business Scenarios

Targeting value customers such as platinum cardholders, launch a "cashback on purchases for the next N months" campaign:

- Campaigns select customers by card product tier (for example PLATINUM, GOLD);
- A campaign has explicit start and end dates, lasting 3 months or any 1–24 months;
- After a customer enrolls, every qualifying purchase during the campaign period earns proportional cashback;
- Cashback is capped per transaction, per customer in aggregate, and by the campaign budget;
- Cashback posts to the credit card account in real time (reducing outstanding debt) and can be retried on failure;
- Operations can create/activate/pause/end campaigns and view performance.

### 1.2 Acceptance Criteria

1. `durationMonths` is specified when creating a campaign, and the system computes `endDate = startDate + N months - 1 day`.
2. Only purchases within the campaign period, with a matching product tier, enrolled (if enrollment is required), and meeting the amount threshold earn cashback.
3. Cashback = `min(purchase × rate, per-transaction cap, customer remaining allowance, campaign remaining budget)`, 2 decimals.
4. The same campaign + the same transaction earns cashback only once; a repeated posting does not grant cashback again.
5. Cashback reduces outstanding debt in real time through the account-service credit endpoint; on failure the record stays PENDING and is retryable.
6. The campaign budget/customer cap is never breached (within a single instance).
7. The frontend supports: customers view campaigns→enroll→view cashback; admins create→activate→pause/end→view performance.

## 2. Impact Analysis

| Dimension | Impact |
|---|---|
| Code | New marketing-service; account-service gains a generic credit endpoint; transaction-service restores the "evaluate cashback after posting" client; web-ui gains the marketing menu/pages |
| Data | New `carddemo_marketing` (campaign/enrollment/reward_grant); account gains `account_credit` (V6) |
| Interfaces | New campaign/enrollment/eligibility/evaluation/reward/performance APIs; account gains `POST /accounts/{id}/credits` |
| Configuration | transaction gains `MARKETING_SERVICE_URL/MARKETING_ENABLED`; web-ui gains proxy entries; Compose gains the marketing container |
| Documentation | This design and ADR-013 are new; README/service inventory/API catalog/runbook/smoke script are updated |
| Security | Same as the legacy module: the POC does not enforce authentication; the cashback API requires an idempotency key; amounts/rates are boundary-validated |
| Compatibility | The account credit is a new endpoint and does not change existing repayment/posting semantics; the old `reward_credit` is removed in favor of the new `account_credit` |

## 3. Architecture Design

```
transaction-service --(after successful posting, best-effort)--> marketing-service
                                                    |-- AccountClient: product tier / credit cashback
                                                    |-- local: campaign / enrollment / reward_grant
account-service <--(POST /accounts/{id}/credits, idempotent on creditId)-- marketing-service
web-ui -------------------------------------------> marketing-service
```

- A cashback evaluation failure does not affect posting (the money fact takes priority); only a WARN is logged; the reward stays PENDING and can be retried.
- Campaign budget/customer caps are validated inside the marketing database; single-instance demo, cross-instance requires a database row lock/distributed lock (see risks).
- Account credits use the `creditId` unique key, so repeated calls do not reduce the balance twice.

### 3.1 Tables (marketing-service)

**campaign**

`campaign_id`, `code` (unique), `name`, `description`, `status` (DRAFT/ACTIVE/PAUSED/ENDED),
`start_date`, `duration_months`, `end_date`, `eligible_product_codes` (comma separated),
`min_transaction_amount`, `cashback_rate`, `max_cashback_per_transaction`,
`max_cashback_per_customer`, `budget`, `consumed_budget`, `enrollment_required`,
`created_at`, `updated_at`, `version`.

**enrollment**

`enrollment_id`, `campaign_id`, `account_id`, `card_number`, `customer_id`, `status`,
`enrolled_at`, `version`; unique `(campaign_id, account_id)`.

**reward_grant**

`reward_id`, `campaign_id`, `transaction_id`, `account_id`, `customer_id`, `card_number`,
`transaction_amount`, `cashback_amount`, `status` (PENDING/GRANTED/FAILED), `failure_reason`,
`created_at`, `granted_at`, `version`; unique `(campaign_id, transaction_id)`.

### 3.2 Account Credit (account-service, V6)

`account_credit`: `credit_id` (PK), `account_id`, `source_type`, `amount`, `description`, `created_at`.

`POST /api/v1/accounts/{accountId}/credits`:

```json
{"creditId":"reward-uuid","sourceType":"CASHBACK","amount":30.00,"description":"PLATINUM_3M cashback"}
```

- `creditId` is idempotent; amount > 0; the account must exist; the balance may go negative (overpayment).
- Returns the account snapshot.

### 3.3 Key Rules

| ID | Rule |
|---|---|
| PC-01 | `durationMonths` 1–24; `endDate = startDate + N months - 1 day` |
| PC-02 | Cashback rate 0–1; per-transaction/per-customer caps > 0; budget > 0; product tier list non-empty |
| PC-03 | Only ACTIVE campaigns participate in evaluation; the transaction date must fall within `[startDate, endDate]` |
| PC-04 | The account product code is in `eligibleProductCodes` |
| PC-05 | When `enrollmentRequired=true`, the customer must already be enrolled |
| PC-06 | The purchase amount ≥ `minTransactionAmount` |
| PC-07 | Cashback = min(amount×rate, per-transaction cap, customer remaining, budget remaining), HALF_UP 2 decimals |
| PC-08 | Idempotent per campaign+transaction; an already evaluated transaction is skipped |
| PC-09 | The customer cumulative cap is computed over PENDING+GRANTED; budget is reserved when the PENDING record is created |
| PC-10 | A failed cashback credit keeps PENDING; retries are idempotent; on success GRANTED + posting time |

## 4. API Contract

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/campaigns` | Create a campaign (DRAFT, 201) |
| GET | `/api/v1/campaigns?status=&page=&size=` | Campaign list |
| GET | `/api/v1/campaigns/{id}` | Details |
| POST | `/api/v1/campaigns/{id}/activate` | Activate (DRAFT/PAUSED→ACTIVE) |
| POST | `/api/v1/campaigns/{id}/pause` | Pause (ACTIVE→PAUSED) |
| POST | `/api/v1/campaigns/{id}/end` | End (ACTIVE/PAUSED→ENDED) |
| POST | `/api/v1/campaigns/{id}/enrollments` | Enroll (201, idempotent) |
| GET | `/api/v1/campaigns/{id}/enrollments?page=&size=` | Enrollment list |
| GET | `/api/v1/eligibilities?accountId=&cardNumber=&customerId=` | Campaigns visible to the customer and eligibility |
| POST | `/api/v1/reward-evaluations` | Evaluate cashback for a transaction (internal) |
| GET | `/api/v1/rewards?accountId=&customerId=&page=&size=` | Cashback records |
| POST | `/api/v1/rewards/{rewardId}/retry` | Retry a pending posting |
| GET | `/api/v1/campaigns/{id}/performance` | Campaign performance |
| GET | `/api/v1/marketing/summary` | Dashboard summary |

Create campaign example:

```json
{"code":"PLATINUM_3M_CASHBACK","name":"Platinum 3-month purchase cashback",
 "description":"5% cashback on purchases during the campaign, up to 30 per transaction and 300 in total",
 "startDate":"2026-10-01","durationMonths":3,
 "eligibleProductCodes":["PLATINUM"],
 "minTransactionAmount":50.00,"cashbackRate":0.05,
 "maxCashbackPerTransaction":30.00,"maxCashbackPerCustomer":300.00,
 "budget":50000.00,"enrollmentRequired":true}
```

Evaluation request:

```json
{"transactionId":"0000000000000200","accountId":"00000000006",
 "cardNumber":"3344556677889900","customerId":"000000001",
 "amount":1000.00,"occurredAt":"2026-10-26T10:15:00"}
```

Response:

```json
{"grants":[{"rewardId":"...","campaignCode":"PLATINUM_3M_CASHBACK",
            "cashbackAmount":30.00,"status":"GRANTED"}],
 "skipped":[{"campaignCode":"GOLD_6M_CASHBACK","reason":"PRODUCT_NOT_ELIGIBLE"}]}
```

## 5. Frontend

- New top-level menu "Marketing", with two sub-items:
  - `#/premium-offers` (customer): account/card lookup → campaign list/eligibility/enrollment; my cashback records, retry pending postings.
  - `#/premium-campaigns` (admin): create campaigns (duration in months, product tier, rate, caps, budget), list, activate/pause/end, performance.
- The home page gains entries; the proxy routes `/api/v1/campaigns`, `/eligibilities`, `/reward-evaluations`, `/rewards`, `/marketing` → marketing-service.

## 6. Review Checklist

- [ ] The domain does not depend on outer layers other than Spring/JPA;
- [ ] Amount/rate boundaries, campaign duration, idempotency key, budget/cap conflict handling;
- [ ] Marketing failures do not affect posting; retries are idempotent;
- [ ] Account credit `creditId` is unique and the balance may go negative;
- [ ] API error codes/HTTP semantics are consistent; pagination structures are consistent;
- [ ] No sensitive information in logs; no SQL concatenation;
- [ ] Frontend esc/money/date handling; admin permissions.

## 7. Test Plan

- Unit: campaign duration calculation, eligibility (product/window/enrollment/amount), cashback calculation and caps, budget/customer caps, idempotency.
- Integration: create→activate→enroll→evaluate→credit→retry; repeated transactions do not grant cashback twice; budget exhaustion.
- Controller: 201/409/404/400/503.
- Transaction: calls marketing after posting; a marketing failure does not roll back.
- Account: credit idempotency, negative balance allowed, amount validation.
- UI: `node --check`; page/API contracts; full-repository `mvn test`.
- End-to-end: platinum account enrolls → purchase 1000 → cashback 30 → balance drops by 30 → a repeated posting does not grant cashback again.

## 8. Risks and Rollback

- Budget/cap concurrency: a campaign row lock is used for the single-instance demo; multiple instances need a distributed lock or conditional database updates.
- Cashback timeliness: synchronous call, worst case +2s; failures only raise alerts.
- Campaign dates use the server time zone (local demo); production needs a unified UTC/business time zone.
- Rollback: disable `MARKETING_ENABLED` to stop evaluation; remove the Compose marketing service and menus; the account credit endpoint can be kept as a generic capability.

## 9. Review Conclusion

Self-check against the checklist in section 6 is complete:

- The domain layer does not depend on Spring Web; amount/rate/duration/product-tier boundaries are validated in the aggregate root and the calculators.
- The evaluation idempotency key is `(campaignId, transactionId)`; cashback credits are idempotent on `creditId`; a repeated posting only returns duplicates.
- Budget is reserved when the PENDING record is created, the customer cap is aggregated over PENDING+GRANTED; a failed credit keeps PENDING and can be retried.
- Marketing exceptions only log a WARN and do not affect PostingService posting; account credits serialize balance changes with a row lock.
- Controllers uniformly return `{code,message,details?}` and pagination `{items,page,size,total}`; frontend dynamic text is escaped.
- Known limitations: budget/caps are protected for a single instance only; the customerId accepted at enrollment is not cross-checked against card ownership (callers are advised to pass the card's real customer); points/A-B testing/complex segments are out of scope for this phase.

## 10. Test Results

- Full-repository `mvn test`: **all 815 test cases green** (marketing 42, account 39, transaction 64).
- Marketing unit tests cover: 3/6-month durations, product eligibility, enrollment idempotency, cashback calculation with per-transaction/customer/budget caps, transaction idempotency, credit failure PENDING/retry, campaign state transitions.
- End-to-end (Docker Compose):
  - Platinum account 6 enrolls in `PLATINUM_3M_CASHBACK`;
  - purchase 1000.00 → cashback 30.00 (5%, per-transaction cap 30);
  - account balance 2455.75 → 3425.75 (+1000 −30);
  - resubmitting the same transaction id → `duplicates`, and the cashback record count stays 1;
  - campaign performance: 1 enrollment, 1 reward, 1 granted, total cashback 30, budget utilization 0.06%, conversion rate 100%.
- Frontend: `node --check` passes; the proxy works for `/eligibilities`, `/rewards`, `/campaigns`; navigation gains "Marketing" and its two sub-entries.
