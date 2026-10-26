# 限界上下文定义

## 1. 上下文清单

> 说明：`web-ui`（业务界面 + 同源代理）是**表现层**，不属于任何限界上下文，不拥有数据与业务规则。

| 上下文 | 类型 | 服务 | 数据库 | 拥有的聚合 |
|---|---|---|---|---|
| 身份与访问（IAM） | 通用域 | iam-service :8081 | `carddemo_iam` | SecUser |
| 客户（Customer） | 支撑域 | customer-service :8082 | `carddemo_customer` | Customer |
| 卡片（Card） | 核心域 | card-service :8083 | `carddemo_card` | Card、CardXref |
| 账户（Account） | 核心域 | account-service :8084 | `carddemo_account` | Account、AccountProduct、AccountHold |
| 交易与账务（Transaction） | 核心域 | transaction-service :8085 | `carddemo_transaction` | TransactionRecord、TranCategoryBalance、TranType、TranCategory、DisclosureGroup、PostingBatch |
| 总账（Ledger） | 核心域（新增） | ledger-service :8088 | `carddemo_ledger` | LedgerAccount、JournalEntry、AccountingPeriod |
| 对账（Reconciliation） | 支撑域（新增） | reconciliation-service :8089 | `carddemo_reconciliation` | ReconciliationRun、ReconciliationItem、EodRun |
| 支付（Payment） | 核心域（新增） | payment-service :8091 | `carddemo_payment` | PaymentOrder |
| 账单（Billing） | 支撑域（新增） | billing-service :8092 | `carddemo_billing` | Statement |
| 限额（Limit） | 支撑域（新增） | limit-service :8093 | `carddemo_limit` | LimitDefinition |
| 汇率（FX） | 支撑域（新增） | fx-service :8094 | `carddemo_fx` | FxRate、FxQuote、FxConversion |
| 信贷（Loan） | 核心域（新增） | loan-service :8095 | `carddemo_loan` | LoanApplication、Loan、RepaymentSchedule |
| 欺诈（Fraud） | 支撑域（新增） | fraud-service :8096 | `carddemo_fraud` | FraudRule、FraudEvaluation、FraudAlert |
| 反洗钱（AML） | 支撑域（新增） | aml-service :8097 | `carddemo_aml` | SanctionsEntry、ScreeningResult、AmlAlert |
| 争议（Dispute） | 支撑域（新增） | dispute-service :8098 | `carddemo_dispute` | DisputeCase |
| 催收（Collections） | 支撑域（新增） | collections-service :8099 | `carddemo_collections` | DelinquencyCase |
| 批处理（Batch） | 支撑域（新增） | batch-service :8100 | `carddemo_batch` | JobDefinition、JobRun |
| 报表（Reporting） | 支撑域（新增） | reporting-service :8101 | `carddemo_reporting` | ReportDefinition、ReportRun、ReportRow |
| 通知（Notification） | 支撑域（新增） | notification-service :8102 | `carddemo_notification` | NotificationTemplate、Notification |
| 文档（Document） | 支撑域（新增） | document-service :8103 | `carddemo_document` | DocumentMetadata |
| 工作流（Workflow） | 支撑域（新增） | workflow-service :8104 | `carddemo_workflow` | WorkflowDefinition、WorkflowTask |
| 审计（Audit） | 通用域（新增） | audit-service :8105 | `carddemo_audit` | AuditEvent |
| 参考数据（Reference） | 通用域（新增） | reference-data-service :8106 | `carddemo_reference` | ReferenceItem |
| BFF | 表现层（新增） | bff-service :8107 | —（无状态） | CustomerOverview |
| 搜索（Search） | 支撑域（新增） | search-service :8108 | —（无状态） | SearchResult |
| 营销（Marketing） | 支撑域（新增） | marketing-service :8087 | `carddemo_marketing` | Campaign、Enrollment、RewardGrant |
| 授权（Authorization） | 核心域 | authorization-service :8086 | `carddemo_authorization` | AuthorizationCase、PendingAuthorizationSummary、CardFraudFlag |

## 2. 各上下文职责与边界

### 2.1 IAM（iam-service）

- **职责**：登录鉴别、用户增删改查、用户类型（A/U）与菜单分流、token 签发。
- **聚合**：`SecUser`（用户号、姓名、密码哈希、类型、版本）。
- **不变量**：用户号 8 位唯一；类型 ∈ {A,U}；至少保留一个管理员；不能删除自己（可选 `X-User-Id`）。
- **对外契约**：`/api/v1/auth/*`、`/api/v1/users/*`。
- **明确不属于本上下文**：业务权限（菜单项级）、会话管理（无状态 HTTP）、单点登录。
- **遗留映射**：COSGN00C、COADM01C、COMEN01C（仅菜单项数据）、COUSR00C–03C。
- **安全决策**：ADR-007（token 不强制校验）、ADR-008（PBKDF2）。

### 2.2 Customer（customer-service）

- **职责**：客户主数据（姓名、地址、联系方式、SSN、DOB、FICO、EFT、主卡标志）。
- **不变量**：见 BR-CUST-02..12。
- **对外契约**：`GET/POST/PUT /api/v1/customers`。
- **不属于**：账户额度、卡片状态、客户与账户的归属关系（由 card-service 的 Xref 维护）。
- **遗留映射**：CUSTDAT、CBCUS01C、COACTUPC 的客户部分、CBEXPORT/CBIMPORT 的 C 记录。
- **数据最小化**：响应只返回 SSN 掩码；不返回完整 SSN。

### 2.3 Card（card-service）

- **职责**：卡片主数据、卡号↔账户↔客户映射（Xref）、卡列表/详情/维护、按账户查卡。
- **不变量**：见 BR-CARD-01..11、NR-CARD-01/02。
- **对外契约**：`/api/v1/cards`、`/api/v1/cards/by-account/{accountId}`。
- **不属于**：额度与余额（Account）、授权决策（Authorization）、客户资料（Customer）。
- **遗留映射**：CARDDAT、CCXREF、CXACAIX、COCRDLIC/SLC/UPC、CBACT02C、CBACT03C。

### 2.4 Account（account-service）

- **职责**：账户主数据、余额与额度、记账（posting）、还款（payment）、利息入账（interest accrual）。
- **不变量**：见 BR-ACCT-02..06、NR-ACCT-01。
- **对外契约**：`/api/v1/accounts`。
- **不属于**：交易主档（Transaction）、授权占用（Authorization）、客户资料（Customer）。
- **遗留映射**：ACCTDAT、COACTVWC、COACTUPC（账户部分）、CBTRN02C 的账户更新段、CBACT04C 的账户结算、COBIL00C 的扣款段。

### 2.5 Transaction（transaction-service）

- **职责**：交易主档、交易类型/类别参照数据、类别累计、日终入账、利息计提、账单支付编排、交易报表。
- **不变量**：见 BR-TRN-*、BR-POST-*、BR-INT-*、BR-BILL-*。
- **对外契约**：`/api/v1/transactions`、`/api/v1/postings`、`/api/v1/interest-runs`、
  `/api/v1/bill-payments`、`/api/v1/reports/transactions`、`/api/v1/transaction-types`。
- **不属于**：账户余额的最终权威（Account）、卡片状态（Card）、授权决策（Authorization）。
- **遗留映射**：TRANSACT、DALYTRAN、TCATBALF、TRANTYPE、TRANCATG、DISCGRP、COTRN00C/01C/02C、CBTRN01C/02C/03C、CBACT04C、COBIL00C、CORPT00C。

### 2.6 Authorization（authorization-service）

- **职责**：授权决策、待授权汇总与明细、卡级欺诈标记、过期授权清理。
- **不变量**：见 BR-AUTH-01..13、NR-AUTH-01/02。
- **对外契约**：`/api/v1/authorizations`、`/api/v1/pending-authorizations/{accountId}`、
  `/api/v1/authorization-purges`。
- **不属于**：账户余额与额度（只读取快照）、交易入账（授权批准不等于入账）、客户资料。
- **遗留映射**：COPAUA0C、COPAUS0C/1C/2C、CBPAUP0C、IMS PAUTSUM0/PAUTDTL1、DB2 AUTHFRDS。

### 2.7 Ledger（ledger-service，新增）

- **职责**：科目表、复式日记账、冲正、试算平衡、会计期间关账。
- **聚合**：`LedgerAccount`（科目）、`JournalEntry`（日记账，含 `JournalLine`）、`AccountingPeriod`（期间）。
- **不变量**：至少两行、金额 > 0、Σ借 = Σ贷；科目存在且启用；期间未关账；`(sourceType, sourceId)` 幂等。
- **对外契约**：`/api/v1/ledger-accounts`、`/api/v1/journal-entries`、`/api/v1/trial-balance`、
  `/api/v1/accounting-periods`。
- **不属于**：账户余额（Account 权威）、业务交易语义（Transaction 权威）、客户/卡状态。
- **集成**：Transaction 过账成功后同步记日记账（失败只告警）；冲正由两侧各自生成反向记录。

### 2.8 Reconciliation（reconciliation-service，新增）

- **职责**：按业务日期对账过账批次与 `POSTING_BATCH` 日记账，校验试算平衡，记录差异与处理说明，提供 EOD 作业入口。
- **聚合**：`ReconciliationRun`（运行）、`ReconciliationItem`（差异明细）、`EodRun`（作业运行）。
- **不变量**：只读业务与总账，不修改账务；差异处理只更新对账记录；下游失败保留 FAILED 与错误信息。
- **对外契约**：`/api/v1/reconciliation-runs`、`/api/v1/reconciliation-items`、`/api/v1/eod-runs`。
- **不属于**：账户余额、过账规则、日记账写入（分别归 Account/Transaction/Ledger）。
- **集成**：读取 transaction 批次、ledger 日记账与试算平衡；不反向修改两者。

### 2.9 Payment（payment-service，新增）

- **职责**：支付指令受理、收款人筛查、接受、清算（调用 transaction 过账）、退汇（冲正）。
- **聚合**：`PaymentOrder`，状态 RECEIVED→SCREENED→ACCEPTED→SETTLED，RETURNED/REJECTED 终态。
- **不变量**：金额 > 0；币种/类型合法；状态迁移单向；`idempotencyKey` 唯一；清算幂等键 `payment:<id>`。
- **集成**：Account 校验账户；Transaction 落过账批次与冲正；不直接改余额。

### 2.10 Billing（billing-service，新增）

- **职责**：按账户+期间生成账单，计算期初/期末、消费/还款、利息、最低还款、到期日，支持全额支付。
- **聚合**：`Statement`，唯一 `(accountId, period)`；状态 GENERATED/PAID/OVERDUE。
- **集成**：Account 读取账户/产品/周期累计并执行还款。

### 2.11 Limit（limit-service，新增）

- **职责**：客户/账户/渠道的产品限额定义、占用/释放与可用性检查。
- **聚合**：`LimitDefinition`；不变量 amount>0、used≤amount、唯一 `(scopeType, scopeId, limitType, currency)`。
- **边界**：本期独立，授权链路接入为后续。

### 2.12 FX（fx-service，新增）

- **职责**：直接货币对汇率维护、5 分钟报价、单次消费换汇。
- **聚合**：`FxRate`、`FxQuote`、`FxConversion`；报价过期/重复消费拒绝。
- **边界**：仅直接货币对，无三角套算与敞口管理。

### 2.13 P3/P4/P5 扩展上下文（新增）

| 上下文 | 核心职责 | 关键不变量 |
|---|---|---|
| Loan | 申请→审批→放款→还款 | 状态单向；等额本息计划；还款冲抵期次 |
| Fraud | 规则评分→决策→警报 | 分值阈值；REVIEW/REJECT 生成警报 |
| AML | 名单筛查→警报 | 归一化匹配；命中才生成警报 |
| Dispute | 开案→审核→临时入账→chargeback→结案 | 状态单向；临时入账≤争议金额 |
| Collections | 逾期分桶→PTP→结案 | 分桶边界；PTP 未来日期；结案不可变 |
| Batch | 作业定义→运行 | RUNNING→COMPLETED/FAILED；下游失败保留记录 |
| Reporting | 报表定义→运行→结果行 | 行数与下游一致；失败保留 FAILED |
| Notification | 模板→通知→发送 | 模板启用；重复发送幂等 |
| Document | 元数据+文本→归档/删除 | UTF-8 大小；归档单向 |
| Workflow | 定义→任务→审批/拒绝 | 仅 PENDING 可决策；重复决策 409 |
| Audit | 追加事件→检索 | 只追加，无更新/删除 |
| Reference | 参考项增删改查 | `(domain, code)` 唯一；大小写规范化 |
| BFF | 客户 360 聚合 | account 失败 404/503；其他下游降级 |
| Search | 客户/交易搜索 | 类型白名单；下游失败降级 |

### 2.14 Marketing（marketing-service，新增）

- **职责**：高端卡分期返现；活动按产品等级（PLATINUM/GOLD）圈选，期限 1–24 个月；报名、返现评估、奖励重试、活动效果。
- **聚合**：`Campaign`（状态机 DRAFT→ACTIVE⇄PAUSED→ENDED）、`Enrollment`、`RewardGrant`（幂等键 campaignId+transactionId）。
- **不变量**：费率 0–1；单笔/客户/预算上限；返现 PENDING 预占；`(sourceType,sourceId)` 由账户贷记保证幂等。
- **对外契约**：`/api/v1/campaigns`、`/eligibilities`、`/reward-evaluations`、`/rewards`、`/marketing`。
- **集成**：Transaction 入账后评估（失败只告警）；Account 提供账户产品等级与贷记入账。

## 3. 跨上下文协作（谁问谁答）

| 场景 | 发起方 | 依赖 | 数据流向 |
|---|---|---|---|
| 交易录入 | Transaction | Card | 卡号 → 账户/客户 |
| 入账 | Transaction | Card、Account | 校验卡与账户；写账户余额 |
| 账单支付 | Transaction | Account、Card | 读余额；写交易；扣款 |
| 利息 | Transaction | Account | 写利息交易；余额与账期结算 |
| 授权决策 | Authorization | Card、Account、Customer | 读卡/账户/客户快照；写本地授权汇总 |
| 过账 | Transaction | Account、Ledger | 账户余额变更；批次与分录落库；总账同步 |
| 总账查询/关账 | Web UI → Ledger | — | 只读本地科目、分录与期间 |
| 对账 | Reconciliation | Transaction、Ledger | 读取批次/日记账/试算平衡；写本地对账记录 |
| EOD | Web UI → Reconciliation | Transaction、Ledger | 触发对账并记录作业结果 |
| 支付清算 | Payment | Account、Transaction | 校验账户；创建过账批次/冲正 |
| 账单生成/支付 | Billing → Account | Account | 读取账户/产品/周期累计；执行还款 |
| 限额检查 | 调用方 → Limit | — | 占用/释放/可用性检查 |
| 换汇 | 调用方 → FX | — | 报价与换汇记录 |
| 贷款 | 调用方 → Loan | — | 申请/审批/放款/还款 |
| 风险处置 | 调用方 → Fraud/AML/Dispute/Collections | — | 评分/筛查/案件状态机 |
| 运营作业 | 调用方 → Batch/Reporting/Notification/Document/Workflow/Audit/Reference | 下游按需 | 作业、报表、通知、文档、审批、审计、参考数据 |
| 客户 360 | Web UI → BFF | Account/Card/Customer/Transaction | 聚合与降级 |
| 统一搜索 | Web UI → Search | Customer/Transaction | 实时过滤 |
| 交易报表 | Transaction | — | 只读本地交易与参照数据 |
| 账户视图 | 调用方（UI/BFF） | Account、Customer、Card | 三次查询组合 |

## 4. 哪些遗留职责被合并/移动

| 遗留职责 | 新归属 | 说明 |
|---|---|---|
| `CXACAIX` 账户→客户/卡查询 | Card | Xref 是卡片上下文的映射数据 |
| 交易列表按卡号过滤 | Transaction（本地有 `card_number`） | 无需调用 Card |
| 报表按卡号分组 | Transaction | 交易主档已含卡号 |
| 交易类型维护 | Transaction | 参照数据属于交易语义 |
| 分行迁移导入导出 | 独立迁移工具（本期不实现） | 一次性实施工具，不是业务能力 |
| MQ 账户查询 | Account 的 HTTP API + 适配器（延后） | 契约等价 |

## 5. 上下文边界的验收方式

1. 数据库层：服务账号只能访问自己的 database（Compose 中每个服务独立连接串）。
2. 代码层：`domain` 包不得出现 `RestClient`/`JdbcTemplate` 以外跨上下文实体引用。
3. 契约层：跨上下文只使用 `docs/design/05-api-catalog.md` 中的 DTO，不共享 JPA 实体。
4. 测试层：每个服务的端到端测试只依赖自身数据库与 Mock 的下游。
