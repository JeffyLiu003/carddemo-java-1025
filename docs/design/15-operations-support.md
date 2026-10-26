# 15 运营支撑切片 P4

> 上游：`09-scale-up-blueprint.md` P4
> 状态：已实现（P4）

## 1. 范围

| 服务 | 端口 | 数据库 | 能力 |
|---|---|---|---|
| reporting-service | 8101 | `carddemo_reporting` | 报表定义、运行、结果行（交易汇总/试算平衡） |
| notification-service | 8102 | `carddemo_notification` | 模板、通知、发送状态 |
| document-service | 8103 | `carddemo_document` | 文档元数据、文本内容、归档 |
| workflow-service | 8104 | `carddemo_workflow` | 流程定义、任务、审批/拒绝 |
| audit-service | 8105 | `carddemo_audit` | 追加式审计事件、检索 |
| reference-data-service | 8106 | `carddemo_reference` | 币种/国家/假日/产品类别等参考数据 |

## 2. reporting-service

### 2.1 表

- `report_definition`：`report_code`、`name`、`description`、`source_service`、`active`、`version`。
- `report_run`：`run_id`、`report_code`、`business_date`、`status`（RUNNING/COMPLETED/FAILED）、
  `generated_at`、`completed_at`、`row_count`、`message`、`version`。
- `report_row`：`row_id`、`run_id`、`line_no`、`dimension`、`amount`、`detail`。

### 2.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/reports` | 报表定义 |
| POST | `/api/v1/report-runs` | 生成报表（201，body reportCode/businessDate） |
| GET | `/api/v1/report-runs/{runId}` | 运行详情 + rows |
| GET | `/api/v1/report-runs?reportCode=&page=&size=` | 运行历史 |

种子：`DAILY_TRANSACTION_SUMMARY`（transaction）、`TRIAL_BALANCE`（ledger）。
生成逻辑：
- 交易汇总：调用 transaction `/api/v1/reports/transactions?startDate=&endDate=`（同一天），
  每行 dimension=卡号，amount=该卡合计；另加一行 `TOTAL`。
- 试算平衡：调用 ledger `/api/v1/trial-balance?period=YYYY-MM`，每行 dimension=科目号，
  amount=netAmount，detail=借贷合计。
- 下游失败：run FAILED + message，接口 503。

## 3. notification-service

### 3.1 表

- `notification_template`：`template_code`、`channel`（EMAIL/SMS/PUSH）、`subject`、`body`、`active`、`version`。
- `notification`：`notification_id`、`template_code`、`recipient`、`channel`、`payload`、`status`
  （PENDING/SENT/FAILED）、`created_at`、`sent_at`、`error`、`version`。

种子模板：`PAYMENT_RECEIVED`（EMAIL）、`STATEMENT_READY`（EMAIL）、`OTP`（SMS）。

### 3.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/notification-templates` | 模板 |
| POST | `/api/v1/notification-templates` | 新建（201） |
| POST | `/api/v1/notifications` | 创建通知（201，渲染模板；recipient 含 `fail` 时发送失败可复现） |
| GET | `/api/v1/notifications/{id}` | 详情 |
| POST | `/api/v1/notifications/{id}/send` | 发送（PENDING→SENT/FAILED） |
| GET | `/api/v1/notifications?status=&page=&size=` | 分页 |

模板变量使用 `{{name}}` 占位；payload 为 JSON 字符串/对象。

## 4. document-service

### 4.1 表 `document_metadata`

`document_id`、`document_type`（STATEMENT/CONTRACT/RECEIPT/OTHER）、`owner_type`（CUSTOMER/ACCOUNT）、
`owner_id`、`file_name`、`content_type`、`content_text`、`size_bytes`、`status`（ACTIVE/ARCHIVED）、
`created_at`、`version`。

### 4.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/documents` | 上传元数据+文本（201） |
| GET | `/api/v1/documents/{id}` | 元数据 |
| GET | `/api/v1/documents/{id}/content` | 文本内容（text/plain） |
| GET | `/api/v1/documents?ownerType=&ownerId=&page=&size=` | 分页 |
| POST | `/api/v1/documents/{id}/archive` | 归档 |
| DELETE | `/api/v1/documents/{id}` | 删除元数据 |

## 5. workflow-service

### 5.1 表

- `workflow_definition`：`definition_code`、`name`、`steps`、`active`、`version`。
- `workflow_task`：`task_id`、`definition_code`、`business_ref`、`assignee`、`status`
  （PENDING/APPROVED/REJECTED）、`comment`、`created_at`、`decided_at`、`version`。

种子：`LIMIT_INCREASE`、`PAYMENT_REVIEW`、`DISPUTE_REVIEW`。

### 5.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/workflow-definitions` | 定义 |
| POST | `/api/v1/workflow-tasks` | 建任务（201） |
| GET | `/api/v1/workflow-tasks/{id}` | 详情 |
| GET | `/api/v1/workflow-tasks?status=&assignee=&page=&size=` | 分页 |
| POST | `/api/v1/workflow-tasks/{id}/approve` | 通过（body comment 可选） |
| POST | `/api/v1/workflow-tasks/{id}/reject` | 拒绝 |

只有 PENDING 可决策；重复决策 409。

## 6. audit-service

### 6.1 表 `audit_event`

`event_id`、`actor`、`action`、`resource_type`、`resource_id`、`payload`、`created_at`。
只追加，不提供修改/删除。

### 6.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/audit-events` | 追加事件（201） |
| GET | `/api/v1/audit-events/{id}` | 详情 |
| GET | `/api/v1/audit-events?actor=&resourceType=&resourceId=&page=&size=` | 检索 |

## 7. reference-data-service

### 7.1 表 `reference_item`

`item_id`、`domain`、`code`、`label`、`sort_order`、`active`、`version`，唯一 `(domain, code)`。

种子：CURRENCY（USD/CNY/EUR）、COUNTRY（US/CN/SG）、HOLIDAY（2026-10-01/2026-12-25）、
PRODUCT_CATEGORY（CLASSIC/GOLD/PLATINUM）。

### 7.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/reference/{domain}` | 列表（active 过滤） |
| GET | `/api/v1/reference/{domain}/{code}` | 详情 |
| POST | `/api/v1/reference` | 新增（201） |
| PUT | `/api/v1/reference/{itemId}` | 修改 label/sortOrder/active |
| DELETE | `/api/v1/reference/{itemId}` | 删除 |

## 8. 验收

1. 报表：交易汇总/试算平衡生成成功，行数与下游一致；下游失败 run FAILED。
2. 通知：模板渲染；发送成功/失败状态；重复发送幂等。
3. 文档：元数据+文本存取；归档后 status=ARCHIVED；删除 404。
4. 工作流：任务创建、审批/拒绝；重复决策 409。
5. 审计：追加、按 actor/resource 检索；无更新/删除端点。
6. 参考数据：四类种子可查；新增重复 409；修改/删除生效。
7. 全仓测试全绿；扩展服务可通过 Compose `extended` profile 启动。
