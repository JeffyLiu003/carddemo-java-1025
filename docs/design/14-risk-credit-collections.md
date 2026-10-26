# 14 风险、信贷与运营切片 P3/P4（第一批）

> 上游：`09-scale-up-blueprint.md` P3/P4
> 状态：已实现（P3 第一批 + batch）

## 1. 范围

| 服务 | 端口 | 数据库 | 能力 |
|---|---|---|---|
| loan-service | 8095 | `carddemo_loan` | 贷款申请、审批、放款、还款计划、还款 |
| fraud-service | 8096 | `carddemo_fraud` | 交易欺诈评分、规则、警报与处置 |
| aml-service | 8097 | `carddemo_aml` | 制裁/名单筛查、AML 警报 |
| dispute-service | 8098 | `carddemo_dispute` | 争议案件、举证、临时入账、chargeback |
| collections-service | 8099 | `carddemo_collections` | 逾期分桶、承诺还款、结案 |
| batch-service | 8100 | `carddemo_batch` | 作业定义、EOD 作业执行（计息/对账） |

## 2. loan-service

### 2.1 表

- `loan_application`：`application_id`、`customer_id`、`product_code`、`principal`、`term_months`、
  `annual_rate`、`status`（SUBMITTED/APPROVED/REJECTED/DISBURSED/CLOSED）、`reject_reason`、
  `created_at`、`decided_at`、`version`。
- `loan`：`loan_id`、`application_id`（唯一）、`customer_id`、`principal`、`outstanding_principal`、
  `term_months`、`annual_rate`、`status`（ACTIVE/CLOSED）、`disbursed_at`、`version`。
- `repayment_schedule`：`installment_id`、`loan_id`、`installment_no`、`due_date`、`principal_due`、
  `interest_due`、`total_due`、`status`（PENDING/PAID）、`paid_at`、`version`，唯一 `(loan_id, installment_no)`。

### 2.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/loan-applications` | 提交申请（201） |
| GET | `/api/v1/loan-applications/{id}` | 详情 |
| GET | `/api/v1/loan-applications?page=&size=&status=` | 分页 |
| POST | `/api/v1/loan-applications/{id}/approve` | 审批通过并生成还款计划 |
| POST | `/api/v1/loan-applications/{id}/reject` | 拒绝（body reason） |
| POST | `/api/v1/loans/{loanId}/disburse` | 放款 |
| GET | `/api/v1/loans/{loanId}` | 贷款详情 |
| GET | `/api/v1/loans/{loanId}/schedule` | 还款计划 |
| POST | `/api/v1/loans/{loanId}/repayments` | 还款（body amount） |

计算：

- 月利率 `r = annualRate / 12`；等额本息月供 `M = P×r / (1 - (1+r)^-n)`（HALF_UP 2 位）；
- 每期利息 = 剩余本金×r；本金 = M − 利息；最后一期本金取剩余本金，利息按实际剩余计算；
- 还款按顺序冲抵 PENDING 期次，全部结清后 loan CLOSED。

## 3. fraud-service

### 3.1 表

- `fraud_rule`：`rule_code`、`name`、`rule_type`（AMOUNT_GT/MERCHANT_BLOCKED/COUNTRY_BLOCKED）、
  `threshold_amount`、`match_value`、`score`、`active`、`version`。
- `fraud_evaluation`：`evaluation_id`、`transaction_id`、`account_id`、`amount`、`merchant_name`、
  `country`、`score`、`decision`（APPROVE/REVIEW/REJECT）、`matched_rules`、`created_at`。
- `fraud_alert`：`alert_id`、`evaluation_id`、`account_id`、`score`、`status`
  （OPEN/CONFIRMED/DISMISSED）、`reason`、`created_at`、`resolved_at`、`version`。

### 3.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/fraud-evaluations` | 评分（201） |
| GET | `/api/v1/fraud-evaluations/{id}` | 详情 |
| GET | `/api/v1/fraud-alerts?status=&accountId=&page=&size=` | 警报列表 |
| GET | `/api/v1/fraud-alerts/{id}` | 详情 |
| POST | `/api/v1/fraud-alerts/{id}/confirm` | 确认欺诈 |
| POST | `/api/v1/fraud-alerts/{id}/dismiss` | 排除 |

评分：命中规则分值求和；≥80 REJECT，≥50 REVIEW，否则 APPROVE；REVIEW/REJECT 生成 OPEN 警报。
种子规则：金额>5000（60）、商户 BLOCKED MERCHANT（100）、国家 KP/IR（80）。

## 4. aml-service

### 4.1 表

- `sanctions_entry`：`entry_id`、`name`、`normalized_name`、`country`、`entity_type`、`list_source`、`active`、`version`。
- `screening_result`：`screening_id`、`name`、`country`、`entity_type`、`result`
  （CLEAR/POTENTIAL_MATCH/BLOCKED）、`matched_entry_id`、`score`、`created_at`。
- `aml_alert`：`alert_id`、`screening_id`、`customer_id`、`status`（OPEN/CLEARED/ESCALATED）、
  `details`、`created_at`、`resolved_at`、`version`。

### 4.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/screenings` | 名单筛查（201） |
| GET | `/api/v1/screenings/{id}` | 详情 |
| GET | `/api/v1/aml-alerts?status=&page=&size=` | 警报 |
| POST | `/api/v1/aml-alerts/{id}/clear` | 排除 |
| POST | `/api/v1/aml-alerts/{id}/escalate` | 上报 |

规则：姓名归一化（大写、去空格/标点）；完全匹配 → BLOCKED；包含匹配 → POTENTIAL_MATCH；
命中生成 OPEN 警报（POTENTIAL_MATCH/BLOCKED）。

## 5. dispute-service

### 5.1 表 `dispute_case`

`case_id`、`transaction_id`、`account_id`、`customer_id`、`reason_code`、`amount`、`status`
（OPEN/UNDER_REVIEW/PROVISIONAL_CREDIT/CHARGEBACK/WON/LOST/CLOSED）、`provisional_credit_amount`、
`opened_at`、`updated_at`、`closed_at`、`version`。

### 5.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/disputes` | 开案（201） |
| GET | `/api/v1/disputes/{id}` | 详情 |
| GET | `/api/v1/disputes?status=&accountId=&page=&size=` | 分页 |
| POST | `/api/v1/disputes/{id}/review` | 受理审核 |
| POST | `/api/v1/disputes/{id}/provisional-credit` | 临时入账（body amount） |
| POST | `/api/v1/disputes/{id}/chargeback` | 发起 chargeback |
| POST | `/api/v1/disputes/{id}/resolve` | 结案（body won=true/false） |

状态迁移：OPEN→UNDER_REVIEW→PROVISIONAL_CREDIT→CHARGEBACK→WON/LOST→CLOSED；非法迁移 409。
临时入账金额不得超过争议金额。

## 6. collections-service

### 6.1 表 `delinquency_case`

`case_id`、`account_id`、`days_past_due`、`bucket`（B1/B2/B3/B4）、`outstanding_amount`、`status`
（OPEN/PTP/RESOLVED）、`promise_date`、`created_at`、`updated_at`、`resolved_at`、`version`。

### 6.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/delinquency-cases` | 建案（201） |
| GET | `/api/v1/delinquency-cases/{id}` | 详情 |
| GET | `/api/v1/delinquency-cases?status=&bucket=&page=&size=` | 分页 |
| POST | `/api/v1/delinquency-cases/{id}/recalculate` | 更新逾期天数并重算分桶 |
| POST | `/api/v1/delinquency-cases/{id}/promise-to-pay` | 登记承诺还款（body date） |
| POST | `/api/v1/delinquency-cases/{id}/resolve` | 结案 |

分桶：1–30 B1、31–60 B2、61–90 B3、90+ B4；PTP 需未来日期；RESOLVED 后不可再变更。

## 7. batch-service

### 7.1 表

- `job_definition`：`job_code`、`name`、`description`、`target_service`、`active`、`version`。
- `job_run`：`run_id`、`job_code`、`business_date`、`status`（RUNNING/COMPLETED/FAILED）、
  `started_at`、`completed_at`、`message`、`version`。

种子作业：`INTEREST_RUN`（transaction `/api/v1/interest-runs`）、
`RECONCILIATION`（reconciliation `/api/v1/reconciliation-runs`）。

### 7.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/jobs` | 作业定义 |
| POST | `/api/v1/job-runs` | 执行作业（201，body jobCode/businessDate） |
| GET | `/api/v1/job-runs/{id}` | 详情 |
| GET | `/api/v1/job-runs?jobCode=&status=&page=&size=` | 历史 |

执行：先落 RUNNING；调用目标服务；成功 COMPLETED + message；失败 FAILED + message 并返回 503。
同一 jobCode+businessDate 可重复执行，保留每次运行记录。

## 8. 验收

1. 贷款：申请→审批生成等额本息计划→放款→部分/全额还款；计划金额与余额一致。
2. 欺诈：高分交易 REJECT 并生成警报；确认/排除后状态正确。
3. AML：完全匹配 BLOCKED、包含匹配 POTENTIAL_MATCH、无匹配 CLEAR；警报可处置。
4. 争议：合法状态链路可走通；临时入账超金额 409；结案状态正确。
5. 催收：分桶边界正确；PTP 需未来日期；结案后不可再变更。
6. 批处理：计息/对账作业执行并记录 COMPLETED/FAILED。
7. 全仓测试全绿；Docker Compose 可通过 profile 启动扩展服务。
