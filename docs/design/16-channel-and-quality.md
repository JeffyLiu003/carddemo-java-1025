# 16 渠道与体验切片 P5 + 质量收口 P6

> 上游：`09-scale-up-blueprint.md` P5/P6
> 状态：已实现（P5/P6，边界见 §6）

## 1. P5 范围

| 服务/页面 | 端口 | 数据库 | 能力 |
|---|---|---|---|
| bff-service | 8107 | —（无状态） | 客户 360 聚合（账户/卡片/客户/交易/权益） |
| search-service | 8108 | —（无状态） | 客户/交易统一搜索 |
| web-ui `#/portal` | — | — | 客户 360 视图（调用 BFF） |
| web-ui `#/lending` | — | — | 贷款申请/审批/放款/还款 |
| web-ui `#/risk` | — | — | 欺诈/AML/争议/催收 |
| web-ui `#/operations` | — | — | 批处理/报表/通知/文档/工作流/审计/参考数据 |

多币种：本期由 fx-service（报价/换汇）+ reference-data（币种）提供定价能力；
账户级多币种余额与多实体核算留待后续（见 §6）。

## 2. bff-service

### 2.1 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/bff/customer-overview?accountId=00000000001` | 客户 360 聚合 |

响应：

```json
{"accountId":"00000000001",
 "position":{"productCode":"CLASSIC","creditLimit":2020.00,"currentBalance":1940.00,
             "activeHoldAmount":0.00,"availableCredit":80.00},
 "cards":[{"cardNumber":"0500024453765740","customerId":"000000001","activeStatus":"Y"}],
 "customer":{"customerId":"000000001","firstName":"Immanuel","lastName":"Kessler"},
 "recentTransactions":[{"transactionId":"...","amount":50.47,"description":"..."}],
 "offers":[{"campaignId":"...","name":"开卡首刷礼","eligible":true,"enrolled":false}],
 "degraded":[]}
```

聚合规则：

- Account `/credit-position` 失败 → 404（账户不存在）或 503；
- Card/Customer/Transaction/Marketing 失败 → 对应节点为空，`degraded` 记录服务名；不影响整体 200；
- 超时 2s；客户从第一张卡解析 customerId。

## 3. search-service

### 3.1 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/search?q=&type=ALL&limit=10` | 统一搜索 |

规则：

- `type` ∈ ALL/CUSTOMER/TRANSACTION。
- 客户：调用 customer `/api/v1/customers?page=0&size=200`，本地按 customerId/姓名包含过滤。
- 交易：11 位数字 → accountId；16 位数字 → cardNumber 或 idPrefix；其他不查交易。
- 返回 `{items:[{type,id,title,subtitle,amount,reference}], total, degraded}`。
- 下游失败 → 对应类型为空 + degraded，不返回 5xx（除非全部失败且查询类型明确）。

## 4. 前台页面

- `#/portal`：输入账户号，调用 BFF 展示额度位置、卡片、客户、最近交易、权益；degraded 提示。
- `#/lending`：贷款申请创建/列表；审批/拒绝/放款；贷款详情、还款计划、还款。
- `#/risk`：四个 Tab（欺诈、AML、争议、催收），各自列表/创建/处置。
- `#/operations`：七个 Tab（批处理、报表、通知、文档、工作流、审计、参考数据），各自列表/执行/处置。
- 代理新增 `/api/v1/bff`、`/api/v1/search`、`/api/v1/loan-applications`、`/api/v1/loans`、
  `/api/v1/fraud-evaluations`、`/api/v1/fraud-alerts`、`/api/v1/screenings`、`/api/v1/aml-alerts`、
  `/api/v1/disputes`、`/api/v1/delinquency-cases`、`/api/v1/jobs`、`/api/v1/job-runs`、
  `/api/v1/reports`、`/api/v1/report-runs`、`/api/v1/notification-templates`、`/api/v1/notifications`、
  `/api/v1/documents`、`/api/v1/workflow-definitions`、`/api/v1/workflow-tasks`、
  `/api/v1/audit-events`、`/api/v1/reference`。

## 5. P6 交付物

1. **性能**：`scripts/perf/k6-smoke.js`，覆盖登录、账户、过账、对账查询；阈值 p95 < 800ms、错误率 < 1%。
2. **混沌/降级**：`scripts/chaos-test.sh` 停止 marketing，验证交易入账仍成功且奖励降级；恢复后验证。
3. **安全**：`docs/ops/02-security-and-performance.md` 记录 OWASP 核对、依赖扫描命令、NFR 指标。
4. **覆盖率**：父 POM 增加 JaCoCo（`mvn verify` 生成报告，不设硬门槛，避免演示环境阻塞）。
5. **架构约束**：在 account-service 增加 ArchUnit 测试，校验 domain 不依赖 infrastructure/interfaces。
6. **文档**：README 服务表/页面表/测试数/容器数；runbook 增加 extended profile、性能与混沌命令。

## 6. 明确未实现（P5/P6 边界）

- 账户级多币种余额、多实体/多租户核算；
- 真实 OpenSearch/ES 索引（搜索为跨服务实时过滤）；
- API 网关（web-ui 同源代理承担边缘网关职责，BFF 负责聚合）；
- 独立客户门户/移动端 App（web-ui 内以 `#/portal` 呈现）；
- 全链路压测环境、混沌平台与 SIEM 接入。

## 7. 验收

1. BFF 聚合返回账户/卡片/客户/交易/权益；单一下游失败只降级不整体失败。
2. 搜索按客户/交易类型返回结果；非法 type 400。
3. 四个新页面可操作核心链路；导航/代理正确。
4. k6 脚本可执行；chaos 脚本可停止/恢复 marketing。
5. `mvn verify` 生成 JaCoCo 报告；ArchUnit 通过。
6. 默认 Compose 17 个容器（核心 15 + BFF + 搜索）；`--profile extended` 追加 12 个扩展服务。
