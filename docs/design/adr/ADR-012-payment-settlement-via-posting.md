# ADR-012: Payment Settlement Recorded through Posting Batches

| Item | Content |
|----|------|
| ID | ADR-012 |
| Date | 2026-10-26 |
| Status | Accepted |
| Related requirements | `docs/design/13-payments-billing-limits-fx.md` §2 |

## Context

payment-service needs to actually reduce the payer account balance when settling (settle). The possible paths are:

1. Call account-service `/postings` directly to change the balance;
2. Call transaction-service to create a `PAYMENT` posting batch;
3. Write ledger journal entries directly.

The following must all be satisfied: traceable accounting, balanced debits and credits, reversibility of returned payments, and idempotent retries.

## Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| A: change the balance directly | payment → account `/postings` | Shortest path | No debit/credit entries; returned payments need separate compensation; weak audit |
| B: posting batch (adopted) | payment → transaction `POST /posting-batches` (type=PAYMENT) | Reuses rules/entries/reversal/general ledger; clear idempotency key | Depends on transaction; one extra hop |
| C: write the general ledger directly | payment → ledger `/journal-entries` | Entries written directly | Account balances and general ledger may diverge; bypasses business rules |

## Decision

Adopt Option B:

1. During settlement, create a `PAYMENT` posting batch with `payment:<paymentId>` as the idempotency key (accountEffect=DECREASE).
2. After the batch succeeds and returns `transactionBatchId`, the payment order is set to SETTLED; a repeated settle returns directly.
3. For a returned payment that has already settled, call `POST /posting-batches/{batchId}/reversal` to reverse it, then set the order to RETURNED.
4. A downstream failure returns 503 and leaves the payment order status unchanged, allowing retries; account balances and the ledger are never modified directly.

## Consequences

- The payment path depends on transaction-service; when it is unavailable, payments cannot be settled (fail closed).
- General ledger synchronization remains transaction's best-effort strategy (ADR-010), and payment orders are unaware of it.
- When connecting to a real settlement network in the future, only the settlement channel adapter needs replacing; the posting path stays the same.
- Cut-off times, real messages, and return reason codes are not implemented yet; these are demo-level boundaries.

## Evidence

- `docs/design/13-payments-billing-limits-fx.md` §2
- `services/payment-service/src/main/java/com/carddemo/payment/application/PaymentOrderService.java`
- `docs/design/adr/ADR-010-ledger-and-posting-separation.md`
