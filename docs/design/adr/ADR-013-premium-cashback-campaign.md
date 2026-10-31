# ADR-013: Premium Card Installment Cashback Uses an Independent Marketing Context

| Item | Content |
|----|------|
| ID | ADR-013 |
| Date | 2026-10-26 |
| Status | Accepted |
| Related requirements | `docs/design/17-premium-cashback-campaign.md` |

## Context

The old marketing capability covered campaigns, enrollment, points, cashback, budget, and effectiveness; its scope was broad and it has been removed.
The new requirement focuses on cashback for spending by high-value customers such as platinum cardholders over the next N months (3 months by default).
The following must be decided:

1. Whether marketing returns as an independent service;
2. How cashback is posted;
3. How campaign duration and product targeting are modeled;
4. Whether marketing failures affect transaction posting.

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| A: marketing logic embedded in transaction-service | Compute cashback and call the account directly during posting | One fewer service | The transaction service becomes heavier; campaign configuration/operations APIs are hard to evolve independently |
| B: independent marketing-service + account credit (adopted) | Evaluate synchronously after transaction posting; the marketing service manages campaigns/enrollment/rewards; cashback calls account credit | Clear boundaries; money facts and marketing benefits are separated; can be operated independently | One extra hop; eventual consistency |
| C: MQ event-driven | Posting emits events, marketing consumes asynchronously | Decoupled and replayable | Requires MQ and consumption governance; high cost for this phase |

## Decision

Adopt Option B with the following constraints:

1. marketing-service is only responsible for campaigns, enrollment, cashback calculation, and reward records; account balances remain authoritative in account-service.
2. After transaction-service posts successfully, it calls `POST /reward-evaluations`; failures only produce a `WARN` and do not roll back the posting.
3. Cashback is posted via `POST /accounts/{id}/credits` with an idempotent `creditId`; balances may go negative (overpayment).
4. Campaigns define their duration with `startDate + durationMonths`; customers are targeted by product tier (PLATINUM/GOLD).
5. Rewards are idempotent by `(campaignId, transactionId)`; budget/customer caps are validated in the marketing database.
6. This phase does not restore points, A/B testing, or a complex customer-segment DSL; they can be extended within the same context later.

## Consequences

- Transactions still succeed when marketing is unavailable, and cashback may be delayed; PENDING rewards can be retried manually.
- Budget/cap enforcement is currently single-instance only; multiple instances require row locks or conditional updates (already listed as a risk).
- The account credit API is a generic capability that can be reused by other benefit scenarios.
- The old `08-marketing-capability.md` and ADR-009 are not restored; the new design follows this ADR and document 17.

## Evidence

- `docs/design/17-premium-cashback-campaign.md`
- `services/marketing-service/src/main/java/com/carddemo/marketing/domain/service/RewardCalculator.java`
- `services/account-service/src/main/java/com/carddemo/account/application/AccountApplicationService.java`
- `services/transaction-service/src/main/java/com/carddemo/transaction/application/PostingService.java`
