# 微服务拆分与部署设计

## 1. 服务清单

| 服务 | 端口 | 数据库 | 核心用例 | 关键依赖 |
|---|---|---|---|---|
| web-ui | 8090 → 容器 8080 | —（静态 SPA + 同源代理） | 登录、账户视图、卡片、交易、账单、报表、授权、过账、总账、对账/EOD、用户管理 | 全部微服务 |
| iam-service | 8081 | `carddemo_iam` | 登录、token 签发、用户 CRUD | 无 |
| customer-service | 8082 | `carddemo_customer` | 客户查询/新增/修改 | 无 |
| card-service | 8083 | `carddemo_card` | 卡列表/详情/维护、按账户查卡 | 无 |
| account-service | 8084 | `carddemo_account` | 账户查询/维护、产品目录、额度与可用额度、资金冻结、记账、还款、利息入账 | 无 |
| transaction-service | 8085 | `carddemo_transaction` | 交易录入/查询、入账、利息、账单支付、报表、交易类型、过账引擎 | card、account、marketing、ledger |
| ledger-service | 8088 | `carddemo_ledger` | 科目表、日记账、冲正、试算平衡、期间关账 | 无 |
| reconciliation-service | 8089 | `carddemo_reconciliation` | 批次↔日记账对账、差异处理、EOD 作业 | transaction、ledger |
| payment-service | 8091 | `carddemo_payment` | 支付受理/筛查/接受/清算/退汇 | account、transaction |
| billing-service | 8092 | `carddemo_billing` | 账单生成、利息/最低还款、账单支付 | account |
| limit-service | 8093 | `carddemo_limit` | 限额定义、占用/释放、可用性检查 | 无 |
| fx-service | 8094 | `carddemo_fx` | 汇率、报价、换汇 | 无 |
| loan-service | 8095 | `carddemo_loan` | 贷款申请/审批/放款/还款计划 | 无 |
| fraud-service | 8096 | `carddemo_fraud` | 欺诈评分/警报 | 无 |
| aml-service | 8097 | `carddemo_aml` | 名单筛查/AML 警报 | 无 |
| dispute-service | 8098 | `carddemo_dispute` | 争议/chargeback | 无 |
| collections-service | 8099 | `carddemo_collections` | 逾期分桶/催收 | 无 |
| batch-service | 8100 | `carddemo_batch` | 作业定义/EOD 执行 | transaction、reconciliation |
| reporting-service | 8101 | `carddemo_reporting` | 交易汇总/试算平衡报表 | transaction、ledger |
| notification-service | 8102 | `carddemo_notification` | 模板/通知/发送 | 无 |
| document-service | 8103 | `carddemo_document` | 文档元数据/内容 | 无 |
| workflow-service | 8104 | `carddemo_workflow` | 流程定义/任务审批 | 无 |
| audit-service | 8105 | `carddemo_audit` | 追加式审计事件 | 无 |
| reference-data-service | 8106 | `carddemo_reference` | 参考数据 | 无 |
| bff-service | 8107 | — | 客户 360 聚合 | account、card、customer、transaction、marketing |
| search-service | 8108 | — | 客户/交易搜索 | customer、transaction |
| authorization-service | 8086 | `carddemo_authorization` | 授权决策、待授权汇总、欺诈标记、过期清理 | card、account、customer |
| marketing-service | 8087 | `carddemo_marketing` | 活动/报名/奖励评估/效果统计 | account |

## 2. 数据所有权

| 表 | 属主 | 说明 |
|---|---|---|
| `sec_user` | iam | 用户安全主数据 |
| `customer` | customer | 客户主数据（SSN 掩码输出） |
| `card`、`card_xref`、`card_fraud_flag`（授权侧独立表） | card / authorization | 卡主数据归 card；欺诈标记归 authorization |
| `account` | account | 余额与额度唯一权威 |
| `transaction`、`daily_transaction`、`tran_category_balance`、`tran_type`、`tran_category`、`disclosure_group` | transaction | 交易账本与参照数据 |
| `posting_type`、`posting_rule`、`posting_batch`、`posting_leg` | transaction | 过账类型、规则、批次与借贷分录 |
| `ledger_account`、`accounting_period`、`journal_entry`、`journal_line` | ledger | 科目表、期间、日记账与分录行 |
| `reconciliation_run`、`reconciliation_item`、`eod_run` | reconciliation | 对账运行、差异明细与 EOD 作业记录 |
| `payment_order` | payment | 支付指令与状态机 |
| `statement` | billing | 账单（账户+期间唯一） |
| `limit_definition` | limit | 限额与用量 |
| `fx_rate`、`fx_quote`、`fx_conversion` | fx | 汇率、报价与换汇记录 |
| `loan_application`、`loan`、`repayment_schedule` | loan | 贷款申请、贷款与还款计划 |
| `fraud_rule`、`fraud_evaluation`、`fraud_alert` | fraud | 规则、评分与警报 |
| `sanctions_entry`、`screening_result`、`aml_alert` | aml | 名单、筛查与警报 |
| `dispute_case` | dispute | 争议案件 |
| `delinquency_case` | collections | 逾期案件 |
| `job_definition`、`job_run` | batch | 作业定义与运行 |
| `report_definition`、`report_run`、`report_row` | reporting | 报表定义/运行/结果 |
| `notification_template`、`notification` | notification | 模板与通知 |
| `document_metadata` | document | 文档元数据与文本 |
| `workflow_definition`、`workflow_task` | workflow | 流程定义与任务 |
| `audit_event` | audit | 追加式审计事件 |
| `reference_item` | reference | 参考数据 |
| `auth_summary`、`auth_detail` | authorization | 待授权汇总与明细 |
| `campaign`、`enrollment`、`reward_grant` | marketing | 活动、报名与奖励（奖励评估与幂等） |
| `reward_credit` | account | 返现入账幂等记录（rewardId 唯一） |

## 3. 服务间调用矩阵

| 调用方 → 被调方 | Card | Account | Customer | Transaction | Marketing | Ledger | Reconciliation |
|---|---|---|---|---|---|---|---|
| transaction-service | GET 卡快照 | GET 快照 / POST 记账 / POST 还款 / POST 利息 | — | — | POST 奖励评估 | POST 日记账 | — |
| authorization-service | GET 卡快照 | GET 快照 | GET 客户存在性 | — | — | — | — |
| marketing-service | — | GET 快照 / POST 返现入账 | — | — | — | — | — |
| reconciliation-service | — | — | — | GET 过账批次 | — | GET 日记账/试算平衡 | — |
| payment-service | — | GET 账户 | — | POST 过账/冲正 | — | — | — |
| billing-service | — | GET 账户/产品、POST 还款 | — | — | — | — | — |
| ledger-service | — | — | — | — | — | — | — |
| account-service | — | — | — | — | — | — |
| card-service | — | — | — | — | — | — |
| customer-service | — | — | — | — | — | — |
| iam-service | — | — | — | — | — | — |

调用次数（单次业务操作）：

| 业务操作 | 调用链 | 最坏增时（本地 Compose） |
|---|---|---|
| 交易录入 | transaction → card(1) | +10ms |
| 日终入账（单笔） | transaction → card(1) + account GET(1) + account POST(1) | +30ms |
| 账单支付 | transaction → account GET(1) + account POST(1) + card(1) | +30ms |
| 利息运行 | transaction → account POST(每账户 1 次) | 账户数 × 10ms |
| 授权决策 | authorization → card(1) + account(1) + customer(1) | +30ms |
| 消费入账后奖励评估 | transaction → marketing(1)；marketing → account GET(1) + cashback POST(1) | +40ms（失败仅告警） |
| 活动中心 | marketing → account GET(1) | +10ms |
| 创建过账批次 | transaction → account GET(1) + account POST(1) + ledger POST(1) | +40ms（总账失败仅告警） |
| 执行对账 | reconciliation → transaction GET(≤40 页) + ledger GET(≤40 页) + trial GET(1) | 数据量相关；本地 Compose 约 +50ms |
| 执行 EOD | reconciliation → 对账(1) | 同对账 |
| 支付清算 | payment → account GET(1) + transaction POST(1) | +30ms |
| 支付退汇 | payment → transaction POST reversal(1) | +20ms |
| 账单生成 | billing → account GET(2) | +20ms |
| 账单支付 | billing → account POST payments(1) | +10ms |

## 4. 调用与失败语义

| 下游结果 | 上游处理 |
|---|---|
| `200` | 正常继续 |
| `404` | 视为"实体不存在"：入账 → 拒付 101；授权 → 拒绝 3100；交易录入 → 400 |
| `409`（业务规则） | 透传为上游 409，携带下游 `code` 与 `message` |
| `5xx` / 超时（3s） | 抛 `RemoteServiceException` → HTTP 503 `DEPENDENCY_UNAVAILABLE`，不落账、不拒绝（授权返回 503 由调用方重试） |

**失败关闭原则**：授权场景下"无法判断额度"绝不能默认批准。

## 5. 一致性与幂等

- 账单支付：`transaction` 先落库（PENDING）→ 调 `account /payments` → 置 POSTED；失败回滚本地事务。
- 入账：单笔失败记 `REJECTED` + 原因码，整批继续；返回 `COMPLETED_WITH_REJECTS`。
- 幂等键：`transactionId`（入账/录入）；`cardNumber + authTimestamp` 对应 `authorizationId`（授权）；
  `idempotencyKey`（过账批次）；`(sourceType, sourceId)`（总账日记账）。
- 过账：账户调用成功 → 保存批次与分录 → 尽力同步总账；总账失败只告警，批次 `ledgerEntryId` 为空。
- 冲正：生成反向批次/反向日记账，原记录标记 REVERSED，不删除历史。
- 对账：按业务日期比对过账批次与 `POSTING_BATCH` 日记账；试算平衡校验 `Σ借=Σ贷`；
  差异落 `reconciliation_item`，只读账务、不修改余额或分录。账户余额 vs 总账待子账映射。
- 支付：`payment_order.idempotency_key` 唯一；清算过账幂等键 `payment:<paymentId>`；退汇走原批次冲正。
- 账单：`statement (account_id, period)` 唯一；账单支付复用 account `/payments`，重复支付幂等。
- 限额：`(scope_type, scope_id, limit_type, currency)` 唯一；占用/释放走行锁。
- FX：报价 5 分钟有效、单次消费；`fx_conversion.quote_id` 唯一兜底。

## 6. 部署单元

```
docker compose
├── postgres (16)                # 单实例，25 个 database（init 脚本创建，max_connections=300）
├── iam-service        :8081
├── customer-service   :8082
├── card-service       :8083
├── account-service    :8084
├── transaction-service:8085  → 依赖 card/account/marketing/ledger started
├── ledger-service     :8088  → 依赖 postgres healthy
├── reconciliation-service:8089 → 依赖 postgres/transaction/ledger started
├── payment-service    :8091  → 依赖 postgres/account/transaction started
├── billing-service    :8092  → 依赖 postgres/account started
├── limit-service      :8093  → 依赖 postgres healthy
├── fx-service         :8094  → 依赖 postgres healthy
├── bff-service        :8107  → 依赖 account/card/customer/transaction/marketing
├── search-service     :8108  → 依赖 customer/transaction
├── authorization-service:8086 → 依赖 card/account/customer healthy
├── marketing-service  :8087 → 依赖 postgres healthy、account started
└── web-ui             :8090 → 宿主机 8090 映射容器 8080（宿主 8080 被占用）

# docker compose --profile extended up -d 追加：
├── loan-service       :8095
├── fraud-service      :8096
├── aml-service        :8097
├── dispute-service    :8098
├── collections-service:8099
├── batch-service      :8100
├── reporting-service  :8101
├── notification-service:8102
├── document-service   :8103
├── workflow-service   :8104
├── audit-service      :8105
└── reference-data-service:8106
```

> `web-ui` 是**表现层**，不是限界上下文：它只托管静态 SPA，并把 `/api/**` 按路径前缀同源代理到
> 27 个微服务，不拥有任何业务数据、不实现业务规则。浏览器因此只需访问一个源，无需 CORS。

启动顺序：`postgres`（healthcheck: `pg_isready`）→ 无依赖服务 → `ledger` → `transaction` →
`reconciliation` / `authorization` / `web-ui`。
每个服务暴露 Spring Boot Actuator `/actuator/health` 用于探活；下游客户端为懒加载，
服务启动不依赖下游可用，调用失败时返回 `503`。`docker-compose.yml` 中
`transaction`/`authorization` 对下游使用 `condition: service_started`（只保证容器已启动，
避免启动顺序导致的死锁）。

## 7. 容量与伸缩

| 服务 | 伸缩特征 | 备注 |
|---|---|---|
| authorization | 无状态、读多写少，可水平扩容；汇总行按账户存在热点 | 同一账户并发授权需串行（乐观锁重试） |
| transaction | 写多、批处理需单实例运行（避免重复入账） | 入账接口按 `transactionId` 幂等，可多实例 |
| account | 按账户热点；行级更新 | 单账户 QPS 受行锁/版本冲突限制 |
| card / customer | 读多写少 | 可加缓存（本期未做） |
| iam | 低流量 | — |

## 8. 配置项

| 变量 | 服务 | 默认 |
|---|---|---|
| `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` | 全部 | `localhost`/`5432`/`carddemo_*`/`carddemo`/`carddemo` |
| `CARD_SERVICE_URL` | transaction、authorization | `http://localhost:8083`（Compose 内 `http://card-service:8083`） |
| `ACCOUNT_SERVICE_URL` | transaction、authorization | `http://localhost:8084` |
| `CUSTOMER_SERVICE_URL` | authorization | `http://localhost:8082` |
| `MARKETING_SERVICE_URL` | transaction、web-ui | `http://localhost:8087` |
| `MARKETING_ENABLED` | transaction | `true`（关闭后不再触发奖励评估） |
| `IAM_TOKEN_SECRET` | iam | 演示密钥（生产必须从密钥管理注入） |
| `IAM_TOKEN_TTL_SECONDS` | iam | `3600` |
