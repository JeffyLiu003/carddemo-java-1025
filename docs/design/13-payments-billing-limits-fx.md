# 13 支付与计费切片 P2

> 上游：`09-scale-up-blueprint.md` P2
> 状态：已实现（P2）

## 1. 范围

| 服务 | 端口 | 数据库 | 核心能力 |
|---|---|---|---|
| payment-service | 8091 | `carddemo_payment` | 支付指令受理、筛查、清算、退汇 |
| billing-service | 8092 | `carddemo_billing` | 账单生成、最低还款、利息、账单支付 |
| limit-service | 8093 | `carddemo_limit` | 客户/账户/渠道限额、用量与校验 |
| fx-service | 8094 | `carddemo_fx` | 汇率维护、报价、换汇记录 |

支付清算以 transaction-service 的过账批次落账（`PAYMENT` 类型，DECREASE），
账单支付复用 account-service 的 `/payments`；限额与 FX 本期独立，预留后续接入。

## 2. payment-service

### 2.1 表 `payment_order`

`payment_id`、`idempotency_key`（唯一）、`debtor_account_id`、`creditor_name`、
`creditor_account_number`、`creditor_bank_code`、`amount`、`currency`、`payment_type`
（ACH/SEPA/WIRE/INSTANT）、`status`（RECEIVED/SCREENED/ACCEPTED/SETTLED/RETURNED/REJECTED）、
`reference`、`description`、`transaction_batch_id`、`reject_reason`、`created_at`、`updated_at`、
`settled_at`、`returned_at`、`version`。

### 2.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/payment-orders` | 受理支付（201，`idempotencyKey` 幂等） |
| GET | `/api/v1/payment-orders/{paymentId}` | 详情 |
| GET | `/api/v1/payment-orders?page=&size=&status=&accountId=` | 分页 |
| POST | `/api/v1/payment-orders/{paymentId}/accept` | 筛查通过 → ACCEPTED |
| POST | `/api/v1/payment-orders/{paymentId}/settle` | 调 transaction 过账 → SETTLED |
| POST | `/api/v1/payment-orders/{paymentId}/return` | 退汇；已清算则冲正 → RETURNED |

创建请求：

```json
{"idempotencyKey":"pay-20261026-0001","debtorAccountId":"00000000006",
 "creditorName":"Acme Utility","creditorAccountNumber":"1234567890123456",
 "creditorBankCode":"BOFAUS3N","amount":120.00,"currency":"USD",
 "paymentType":"ACH","reference":"INV-2026-10","description":"Utility bill"}
```

规则：

- 金额 > 0、币种 3 位；账户必须存在且 `Y`；`paymentType` 合法。
- 创建后自动筛查：`creditorName` 非空且不在拒绝名单 → `SCREENED`；否则 `REJECTED`。
- `accept` 仅允许 SCREENED；重复调用幂等。
- `settle` 仅允许 ACCEPTED；以 `payment:<paymentId>` 为幂等键调用
  `POST /api/v1/posting-batches`（type `PAYMENT`，accountEffect DECREASE），成功保存 `transactionBatchId`。
- `return` 允许 ACCEPTED/SETTLED；已清算时调用 `POST /api/v1/posting-batches/{batchId}/reversal`。
- 下游不可用 → 503 `DEPENDENCY_UNAVAILABLE`，支付单状态不变。

## 3. billing-service

### 3.1 表 `statement`

`statement_id`、`account_id`、`period`（YYYY-MM）、`cycle_date`、`opening_balance`、`closing_balance`、
`purchases`、`payments`、`fees`、`interest`、`minimum_payment`、`due_date`、`status`
（GENERATED/PAID/OVERDUE）、`generated_at`、`paid_at`、`version`，唯一 `(account_id, period)`。

### 3.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/statements/generate` | 生成账单（201；同账户+期间幂等返回已有） |
| GET | `/api/v1/statements/{statementId}` | 详情 |
| GET | `/api/v1/statements?accountId=&page=&size=` | 分页 |
| POST | `/api/v1/statements/{statementId}/pay` | 全额支付账单（调 account `/payments`） |

生成请求：

```json
{"accountId":"00000000001","period":"2026-10"}
```

计算口径：

- 从 account-service 读取账户与产品：`credit-position` + `account-products/{code}`。
- `closing_balance = currentBalance`；`purchases = currentCycleCredit`；
  `payments = abs(min(currentCycleDebit, 0))`；
  `opening_balance = closing_balance - purchases + payments`。
- `interest = closing_balance × APR ÷ 12`（HALF_UP，2 位）；`fees = 0`（后续接费用引擎）。
- `minimum_payment = closing_balance == 0 ? 0 : max(25.00, closing_balance × 3%)`，且不超过 closing。
- `cycle_date = 期间最后一天`；`due_date = cycle_date + 25 天`。
- `pay`：closing 为 0 直接 PAID；否则调 `POST /accounts/{id}/payments`，成功后 PAID。

## 4. limit-service

### 4.1 表 `limit_definition`

`limit_id`、`scope_type`（CUSTOMER/ACCOUNT/CHANNEL）、`scope_id`、`limit_type`
（PURCHASE/CASH/DAILY/TRANSFER）、`currency`、`amount`、`used_amount`、`active`、`version`；
唯一 `(scope_type, scope_id, limit_type, currency)`。

### 4.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/limits` | 创建限额（201） |
| GET | `/api/v1/limits?scopeType=&scopeId=` | 列表 |
| GET | `/api/v1/limits/{limitId}` | 详情 |
| PUT | `/api/v1/limits/{limitId}` | 调整额度（不得低于已用） |
| POST | `/api/v1/limits/{limitId}/usage` | 占用额度；超限 409 `LIMIT_EXCEEDED` |
| POST | `/api/v1/limits/{limitId}/release` | 释放额度 |
| GET | `/api/v1/limits/check?scopeType=&scopeId=&limitType=&currency=&amount=` | 可用性检查 |

规则：额度 > 0；used ≤ amount；不存在匹配限额时 `allowed=false`；`release` 不得使 used < 0。

## 5. fx-service

### 5.1 表

- `fx_rate`：`pair_id`、`base_currency`、`quote_currency`、`rate`、`effective_at`、`active`、`version`，
  唯一 `(base_currency, quote_currency)`。
- `fx_quote`：`quote_id`、`from_currency`、`to_currency`、`source_amount`、`rate`、`converted_amount`、
  `created_at`、`expires_at`、`consumed`。
- `fx_conversion`：`conversion_id`、`quote_id`、`from_currency`、`to_currency`、`source_amount`、
  `rate`、`converted_amount`、`created_at`。

### 5.2 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/fx/rates` | 汇率列表 |
| POST | `/api/v1/fx/rates` | 维护汇率（upsert） |
| POST | `/api/v1/fx/quotes` | 报价（5 分钟有效） |
| POST | `/api/v1/fx/quotes/{quoteId}/execute` | 执行换汇（quote 只能消费一次） |
| GET | `/api/v1/fx/conversions` | 换汇记录 |

种子：USD/CNY 7.10、USD/EUR 0.92、EUR/CNY 7.72。金额 2 位，汇率 6 位。

## 6. 前台

- `#/payments`（业务）：创建支付、状态筛选、接受/清算/退汇。
- `#/billing`（业务）：生成账单、按账户查询、账单详情、全额支付。
- `#/limits`（管理）：创建/调整限额、占用/释放、可用性检查。
- `#/fx`（业务）：汇率列表、报价、执行换汇、换汇记录。
- 代理新增四组路径；导航与首页入口同步。

## 7. 验收

1. 支付：创建 → 自动筛查 → 接受 → 清算（余额减少、生成总账）→ 退汇（冲正、余额恢复）。
2. 支付幂等：同 `idempotencyKey` 返回同一支付单；重复 settle/return 不重复落账。
3. 账单：同账户+期间只生成一张；金额/利息/最低还款/到期日计算正确；支付后状态 PAID。
4. 限额：占用不超额度；超限 409；释放后可用恢复；无匹配限额时 check 返回 false。
5. FX：报价 5 分钟有效；执行后 quote 不可重复消费；换汇金额计算正确。
6. 前台四条链路可操作；Docker Compose 可启动（当前默认 16 个容器，`--profile extended` 共 28 个）；全仓测试全绿。
