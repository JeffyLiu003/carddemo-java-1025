# 14 Risk, Credit and Operations Slices P3/P4 (First Batch)

> Upstream: `09-scale-up-blueprint.md` P3/P4
> Status: implemented (P3 first batch + batch)

## 1. Scope

| Service | Port | Database | Capability |
|---|---|---|---|
| loan-service | 8095 | `carddemo_loan` | Loan applications, approval, disbursement, repayment schedule, repayment |
| fraud-service | 8096 | `carddemo_fraud` | Transaction fraud scoring, rules, alerts and disposition |
| aml-service | 8097 | `carddemo_aml` | Sanctions/watchlist screening, AML alerts |
| dispute-service | 8098 | `carddemo_dispute` | Dispute cases, evidence submission, provisional credit, chargeback |
| collections-service | 8099 | `carddemo_collections` | Delinquency bucketing, promise to pay, case closure |
| batch-service | 8100 | `carddemo_batch` | Job definitions, EOD job execution (interest accrual/reconciliation) |

## 2. loan-service

### 2.1 Tables

- `loan_application`: `application_id`, `customer_id`, `product_code`, `principal`, `term_months`,
  `annual_rate`, `status` (SUBMITTED/APPROVED/REJECTED/DISBURSED/CLOSED), `reject_reason`,
  `created_at`, `decided_at`, `version`.
- `loan`: `loan_id`, `application_id` (unique), `customer_id`, `principal`, `outstanding_principal`,
  `term_months`, `annual_rate`, `status` (ACTIVE/CLOSED), `disbursed_at`, `version`.
- `repayment_schedule`: `installment_id`, `loan_id`, `installment_no`, `due_date`, `principal_due`,
  `interest_due`, `total_due`, `status` (PENDING/PAID), `paid_at`, `version`, unique `(loan_id, installment_no)`.

### 2.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/loan-applications` | Submit an application (201) |
| GET | `/api/v1/loan-applications/{id}` | Details |
| GET | `/api/v1/loan-applications?page=&size=&status=` | Paged query |
| POST | `/api/v1/loan-applications/{id}/approve` | Approve and generate the repayment schedule |
| POST | `/api/v1/loan-applications/{id}/reject` | Reject (body reason) |
| POST | `/api/v1/loans/{loanId}/disburse` | Disburse |
| GET | `/api/v1/loans/{loanId}` | Loan details |
| GET | `/api/v1/loans/{loanId}/schedule` | Repayment schedule |
| POST | `/api/v1/loans/{loanId}/repayments` | Repay (body amount) |

Calculation:

- Monthly rate `r = annualRate / 12`; equal-installment monthly payment `M = P×r / (1 - (1+r)^-n)` (HALF_UP, 2 decimals);
- Interest per installment = remaining principal × r; principal = M − interest; the last installment takes the remaining principal as principal and computes interest on the actual remaining balance;
- Repayments are applied to PENDING installments in order; once fully repaid the loan is CLOSED.

## 3. fraud-service

### 3.1 Tables

- `fraud_rule`: `rule_code`, `name`, `rule_type` (AMOUNT_GT/MERCHANT_BLOCKED/COUNTRY_BLOCKED),
  `threshold_amount`, `match_value`, `score`, `active`, `version`.
- `fraud_evaluation`: `evaluation_id`, `transaction_id`, `account_id`, `amount`, `merchant_name`,
  `country`, `score`, `decision` (APPROVE/REVIEW/REJECT), `matched_rules`, `created_at`.
- `fraud_alert`: `alert_id`, `evaluation_id`, `account_id`, `score`, `status`
  (OPEN/CONFIRMED/DISMISSED), `reason`, `created_at`, `resolved_at`, `version`.

### 3.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/fraud-evaluations` | Score (201) |
| GET | `/api/v1/fraud-evaluations/{id}` | Details |
| GET | `/api/v1/fraud-alerts?status=&accountId=&page=&size=` | Alert list |
| GET | `/api/v1/fraud-alerts/{id}` | Details |
| POST | `/api/v1/fraud-alerts/{id}/confirm` | Confirm fraud |
| POST | `/api/v1/fraud-alerts/{id}/dismiss` | Dismiss |

Scoring: the scores of matched rules are summed; ≥80 REJECT, ≥50 REVIEW, otherwise APPROVE; REVIEW/REJECT create an OPEN alert.
Seed rules: amount > 5000 (60), merchant BLOCKED MERCHANT (100), country KP/IR (80).

## 4. aml-service

### 4.1 Tables

- `sanctions_entry`: `entry_id`, `name`, `normalized_name`, `country`, `entity_type`, `list_source`, `active`, `version`.
- `screening_result`: `screening_id`, `name`, `country`, `entity_type`, `result`
  (CLEAR/POTENTIAL_MATCH/BLOCKED), `matched_entry_id`, `score`, `created_at`.
- `aml_alert`: `alert_id`, `screening_id`, `customer_id`, `status` (OPEN/CLEARED/ESCALATED),
  `details`, `created_at`, `resolved_at`, `version`.

### 4.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/screenings` | List screening (201) |
| GET | `/api/v1/screenings/{id}` | Details |
| GET | `/api/v1/aml-alerts?status=&page=&size=` | Alerts |
| POST | `/api/v1/aml-alerts/{id}/clear` | Clear |
| POST | `/api/v1/aml-alerts/{id}/escalate` | Escalate |

Rules: names are normalized (uppercase, spaces/punctuation removed); an exact match → BLOCKED; a contains match → POTENTIAL_MATCH;
a hit creates an OPEN alert (POTENTIAL_MATCH/BLOCKED).

## 5. dispute-service

### 5.1 Table `dispute_case`

`case_id`, `transaction_id`, `account_id`, `customer_id`, `reason_code`, `amount`, `status`
(OPEN/UNDER_REVIEW/PROVISIONAL_CREDIT/CHARGEBACK/WON/LOST/CLOSED), `provisional_credit_amount`,
`opened_at`, `updated_at`, `closed_at`, `version`.

### 5.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/disputes` | Open a case (201) |
| GET | `/api/v1/disputes/{id}` | Details |
| GET | `/api/v1/disputes?status=&accountId=&page=&size=` | Paged query |
| POST | `/api/v1/disputes/{id}/review` | Intake review |
| POST | `/api/v1/disputes/{id}/provisional-credit` | Provisional credit (body amount) |
| POST | `/api/v1/disputes/{id}/chargeback` | Initiate a chargeback |
| POST | `/api/v1/disputes/{id}/resolve` | Close the case (body won=true/false) |

State transitions: OPEN→UNDER_REVIEW→PROVISIONAL_CREDIT→CHARGEBACK→WON/LOST→CLOSED; an illegal transition returns 409.
The provisional credit amount must not exceed the disputed amount.

## 6. collections-service

### 6.1 Table `delinquency_case`

`case_id`, `account_id`, `days_past_due`, `bucket` (B1/B2/B3/B4), `outstanding_amount`, `status`
(OPEN/PTP/RESOLVED), `promise_date`, `created_at`, `updated_at`, `resolved_at`, `version`.

### 6.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/delinquency-cases` | Open a case (201) |
| GET | `/api/v1/delinquency-cases/{id}` | Details |
| GET | `/api/v1/delinquency-cases?status=&bucket=&page=&size=` | Paged query |
| POST | `/api/v1/delinquency-cases/{id}/recalculate` | Refresh days past due and recompute the bucket |
| POST | `/api/v1/delinquency-cases/{id}/promise-to-pay` | Record a promise to pay (body date) |
| POST | `/api/v1/delinquency-cases/{id}/resolve` | Close the case |

Buckets: 1–30 B1, 31–60 B2, 61–90 B3, 90+ B4; PTP requires a future date; no further changes are allowed after RESOLVED.

## 7. batch-service

### 7.1 Tables

- `job_definition`: `job_code`, `name`, `description`, `target_service`, `active`, `version`.
- `job_run`: `run_id`, `job_code`, `business_date`, `status` (RUNNING/COMPLETED/FAILED),
  `started_at`, `completed_at`, `message`, `version`.

Seed jobs: `INTEREST_RUN` (transaction `/api/v1/interest-runs`),
`RECONCILIATION` (reconciliation `/api/v1/reconciliation-runs`).

### 7.2 API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/jobs` | Job definitions |
| POST | `/api/v1/job-runs` | Run a job (201, body jobCode/businessDate) |
| GET | `/api/v1/job-runs/{id}` | Details |
| GET | `/api/v1/job-runs?jobCode=&status=&page=&size=` | History |

Execution: persist RUNNING first; call the target service; on success COMPLETED + message; on failure FAILED + message and return 503.
The same jobCode+businessDate can be executed repeatedly, keeping a record of every run.

## 8. Acceptance

1. Loans: application→approval generating an equal-installment schedule→disbursement→partial/full repayment; schedule amounts match the balance.
2. Fraud: a high-scoring transaction is REJECTed and raises an alert; after confirm/dismiss the status is correct.
3. AML: an exact match is BLOCKED, a contains match is POTENTIAL_MATCH, no match is CLEAR; alerts can be dispositioned.
4. Disputes: the legal state chain can be walked through; a provisional credit above the amount returns 409; the closed status is correct.
5. Collections: bucket boundaries are correct; PTP requires a future date; no further changes after closure.
6. Batch: interest accrual/reconciliation jobs execute and record COMPLETED/FAILED.
7. All tests in the repository are green; Docker Compose can start the extension services through a profile.
