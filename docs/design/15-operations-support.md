# 15 Operations Support Slice P4

> Upstream: `09-scale-up-blueprint.md` P4
> Status: implemented (P4)

## 1. Scope

| Service | Port | Database | Capability |
|---|---|---|---|
| reporting-service | 8101 | `carddemo_reporting` | Report definitions, runs, result rows (transaction summary/trial balance) |
| notification-service | 8102 | `carddemo_notification` | Templates, notifications, delivery status |
| document-service | 8103 | `carddemo_document` | Document metadata, text content, archiving |
| workflow-service | 8104 | `carddemo_workflow` | Process definitions, tasks, approval/rejection |
| audit-service | 8105 | `carddemo_audit` | Append-only audit events, search |
| reference-data-service | 8106 | `carddemo_reference` | Reference data such as currencies/countries/holidays/product categories |

## 2. reporting-service

### 2.1 Tables

- `report_definition`: `report_code`, `name`, `description`, `source_service`, `active`, `version`.
- `report_run`: `run_id`, `report_code`, `business_date`, `status` (RUNNING/COMPLETED/FAILED),
  `generated_at`, `completed_at`, `row_count`, `message`, `version`.
- `report_row`: `row_id`, `run_id`, `line_no`, `dimension`, `amount`, `detail`.

### 2.2 API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/reports` | Report definitions |
| POST | `/api/v1/report-runs` | Generate a report (201, body reportCode/businessDate) |
| GET | `/api/v1/report-runs/{runId}` | Run details + rows |
| GET | `/api/v1/report-runs?reportCode=&page=&size=` | Run history |

Seeds: `DAILY_TRANSACTION_SUMMARY` (transaction), `TRIAL_BALANCE` (ledger).
Generation logic:
- Transaction summary: calls transaction `/api/v1/reports/transactions?startDate=&endDate=` (the same day),
  each row has dimension=card number and amount=total for that card; an additional `TOTAL` row is appended.
- Trial balance: calls ledger `/api/v1/trial-balance?period=YYYY-MM`, each row has dimension=ledger account code,
  amount=netAmount, detail=debit/credit totals.
- Downstream failure: the run is FAILED + message and the API returns 503.

## 3. notification-service

### 3.1 Tables

- `notification_template`: `template_code`, `channel` (EMAIL/SMS/PUSH), `subject`, `body`, `active`, `version`.
- `notification`: `notification_id`, `template_code`, `recipient`, `channel`, `payload`, `status`
  (PENDING/SENT/FAILED), `created_at`, `sent_at`, `error`, `version`.

Seed templates: `PAYMENT_RECEIVED` (EMAIL), `STATEMENT_READY` (EMAIL), `OTP` (SMS).

### 3.2 API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/notification-templates` | Templates |
| POST | `/api/v1/notification-templates` | Create (201) |
| POST | `/api/v1/notifications` | Create a notification (201, renders the template; a recipient containing `fail` reproduces a send failure) |
| GET | `/api/v1/notifications/{id}` | Details |
| POST | `/api/v1/notifications/{id}/send` | Send (PENDING→SENT/FAILED) |
| GET | `/api/v1/notifications?status=&page=&size=` | Paged query |

Template variables use the `{{name}}` placeholder; payload is a JSON string/object.

## 4. document-service

### 4.1 Table `document_metadata`

`document_id`, `document_type` (STATEMENT/CONTRACT/RECEIPT/OTHER), `owner_type` (CUSTOMER/ACCOUNT),
`owner_id`, `file_name`, `content_type`, `content_text`, `size_bytes`, `status` (ACTIVE/ARCHIVED),
`created_at`, `version`.

### 4.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/documents` | Upload metadata + text (201) |
| GET | `/api/v1/documents/{id}` | Metadata |
| GET | `/api/v1/documents/{id}/content` | Text content (text/plain) |
| GET | `/api/v1/documents?ownerType=&ownerId=&page=&size=` | Paged query |
| POST | `/api/v1/documents/{id}/archive` | Archive |
| DELETE | `/api/v1/documents/{id}` | Delete metadata |

## 5. workflow-service

### 5.1 Tables

- `workflow_definition`: `definition_code`, `name`, `steps`, `active`, `version`.
- `workflow_task`: `task_id`, `definition_code`, `business_ref`, `assignee`, `status`
  (PENDING/APPROVED/REJECTED), `comment`, `created_at`, `decided_at`, `version`.

Seeds: `LIMIT_INCREASE`, `PAYMENT_REVIEW`, `DISPUTE_REVIEW`.

### 5.2 API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/workflow-definitions` | Definitions |
| POST | `/api/v1/workflow-tasks` | Create a task (201) |
| GET | `/api/v1/workflow-tasks/{id}` | Details |
| GET | `/api/v1/workflow-tasks?status=&assignee=&page=&size=` | Paged query |
| POST | `/api/v1/workflow-tasks/{id}/approve` | Approve (body comment optional) |
| POST | `/api/v1/workflow-tasks/{id}/reject` | Reject |

Only PENDING tasks can be decided; a repeated decision returns 409.

## 6. audit-service

### 6.1 Table `audit_event`

`event_id`, `actor`, `action`, `resource_type`, `resource_id`, `payload`, `created_at`.
Append-only; no update/delete is provided.

### 6.2 API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/audit-events` | Append an event (201) |
| GET | `/api/v1/audit-events/{id}` | Details |
| GET | `/api/v1/audit-events?actor=&resourceType=&resourceId=&page=&size=` | Search |

## 7. reference-data-service

### 7.1 Table `reference_item`

`item_id`, `domain`, `code`, `label`, `sort_order`, `active`, `version`, unique `(domain, code)`.

Seeds: CURRENCY (USD/CNY/EUR), COUNTRY (US/CN/SG), HOLIDAY (2026-10-01/2026-12-25),
PRODUCT_CATEGORY (CLASSIC/GOLD/PLATINUM).

### 7.2 API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/reference/{domain}` | List (active filter) |
| GET | `/api/v1/reference/{domain}/{code}` | Details |
| POST | `/api/v1/reference` | Create (201) |
| PUT | `/api/v1/reference/{itemId}` | Update label/sortOrder/active |
| DELETE | `/api/v1/reference/{itemId}` | Delete |

## 8. Acceptance

1. Reports: transaction summary/trial balance generate successfully and the row count matches downstream; a downstream failure makes the run FAILED.
2. Notifications: template rendering; successful/failed delivery status; repeated sends are idempotent.
3. Documents: metadata + text storage and retrieval; status=ARCHIVED after archiving; deletion returns 404.
4. Workflow: task creation, approval/rejection; a repeated decision returns 409.
5. Audit: appends, search by actor/resource; no update/delete endpoint.
6. Reference data: the four seed domains are queryable; a duplicate create returns 409; updates/deletes take effect.
7. All tests in the repository are green; extension services can be started through the Compose `extended` profile.
