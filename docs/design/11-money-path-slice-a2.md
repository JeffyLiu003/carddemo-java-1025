# 11 钱路径切片 A2：过账引擎与总账

> 上游：`10-money-path-slice-a1.md`（A1.2–A1.4）
> 状态：实施中
> 本轮范围：A1.2 过账引擎（transaction-service）、A1.3 总账（ledger-service）、对应前台页面

## 1. 目标

打通一条完整的资金链路：

```
业务请求 → 过账批次（借贷分录） → 账户余额变更
                              ↘ 总账日记账（复式） → 试算平衡 → 期间关账
```

任何时刻：`Σ 借方 = Σ 贷方`；同一幂等键只产生一个批次；冲正不删除原始分录。

## 2. A1.2 过账引擎（transaction-service）

### 2.1 表（V3 迁移）

- `posting_type`：`type_code`、`name`、`description`、`account_effect`（INCREASE/DECREASE）、`active`、`version`
- `posting_rule`：`rule_id`、`type_code`、`leg_no`、`account_role`、`direction`（DR/CR）、`ledger_account_code`、`description`、`version`，唯一 `(type_code, leg_no)`
- `posting_batch`：`batch_id`、`idempotency_key`（唯一）、`type_code`、`account_id`、`amount`、`currency`、`description`、`status`（POSTED/REVERSED）、`reversal_of`、`ledger_entry_id`、`created_at`、`reversed_at`、`version`
- `posting_leg`：`leg_id`、`batch_id`、`leg_no`、`account_role`、`direction`、`ledger_account_code`、`amount`、`created_at`，唯一 `(batch_id, leg_no)`

### 2.2 种子类型与规则

| typeCode | 名称 | 账户影响 | 分录 |
|---|---|---|---|
| CARD_PURCHASE | 信用卡消费 | INCREASE | DR 1100 / CR 2100 |
| CASH_ADVANCE | 现金预支 | INCREASE | DR 1100 / CR 1000 |
| FEE | 费用 | INCREASE | DR 1100 / CR 4000 |
| INTEREST | 利息 | INCREASE | DR 1100 / CR 4100 |
| PAYMENT | 还款 | DECREASE | DR 1000 / CR 1100 |
| REFUND | 退款 | DECREASE | DR 2100 / CR 1100 |
| ADJUSTMENT | 调账 | INCREASE | DR 1100 / CR 9000 |

### 2.3 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/posting-types` | 类型列表（不含规则） |
| GET | `/api/v1/posting-types/{typeCode}` | 类型 + 规则 |
| POST | `/api/v1/posting-batches` | 创建过账批次 |
| GET | `/api/v1/posting-batches/{batchId}` | 批次详情（含 legs） |
| GET | `/api/v1/posting-batches?page=&size=&accountId=&idempotencyKey=` | 分页查询 |
| POST | `/api/v1/posting-batches/{batchId}/reversal` | 冲正，返回冲正批次 |

创建请求：

```json
{"idempotencyKey":"post-20261026-0001","typeCode":"CARD_PURCHASE",
 "accountId":"00000000006","amount":25.00,"currency":"USD",
 "description":"Purchase at Corner Grocery"}
```

响应（201）：

```json
{"batchId":"...","idempotencyKey":"...","typeCode":"CARD_PURCHASE","typeName":"信用卡消费",
 "accountId":"00000000006","amount":25.00,"currency":"USD","description":"...",
 "status":"POSTED","reversalOf":null,"ledgerEntryId":"...","createdAt":"...","reversedAt":null,
 "legs":[{"legNo":1,"accountRole":"CARD_RECEIVABLE","direction":"DR",
          "ledgerAccountCode":"1100","amount":25.00},
         {"legNo":2,"accountRole":"MERCHANT_PAYABLE","direction":"CR",
          "ledgerAccountCode":"2100","amount":25.00}]}
```

规则：

- 幂等键必填；重复提交且 `typeCode/accountId/amount` 一致返回原批次；不一致 409 `IDEMPOTENCY_CONFLICT`。
- 类型不存在 404 `POSTING_TYPE_NOT_FOUND`；停用 409 `POSTING_TYPE_INACTIVE`。
- 金额必须 > 0（2 位小数），否则 409 `INVALID_POSTING_AMOUNT`。
- 账户不存在 404 `ACCOUNT_NOT_FOUND`；账户非 `Y` 409 `ACCOUNT_INACTIVE`。
- 规则生成的 legs 必须借贷平衡且等于批次金额，否则 409 `UNBALANCED_POSTING`。
- 账户影响：INCREASE 调 `POST /accounts/{id}/postings` 传正数；DECREASE 传负数。
- 账户调用成功后才保存批次；账户调用失败向上抛 503。
- 总账同步为尽力而为：失败只 WARN，不影响批次；成功则写 `ledger_entry_id`。

冲正：

- 原批次已 REVERSED：返回已存在的冲正批次（按 `reversal_of` 查）。
- 冲正批次 `idempotencyKey = "reversal:" + originalBatchId`，legs 方向互换，账户按原影响反向调用。
- 原批次标记 REVERSED + `reversed_at`；冲正批次 `reversal_of` 指向原批次。
- 不允许冲正冲正批次：409 `REVERSAL_NOT_ALLOWED`。

### 2.4 总账客户端

`POST {LEDGER_SERVICE_URL}/api/v1/journal-entries`：

```json
{"sourceType":"POSTING_BATCH","sourceId":"<batchId>","description":"...",
 "postedAt":"2026-10-26T04:00:00","currency":"USD",
 "lines":[{"accountCode":"1100","direction":"DR","amount":25.00},
          {"accountCode":"2100","direction":"CR","amount":25.00}]}
```

配置：`LEDGER_SERVICE_URL`（默认 `http://localhost:8088`）、`LEDGER_SYNC_ENABLED`（默认 true）。

## 3. A1.3 总账（ledger-service，:8088）

### 3.1 表

- `ledger_account`：`account_code`、`name`、`account_type`（ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE）、`parent_code`、`currency`、`active`、`version`
- `accounting_period`：`period`（YYYY-MM）、`status`（OPEN/CLOSED）、`closed_at`、`version`
- `journal_entry`：`entry_id`、`source_type`、`source_id`、`description`、`posted_at`、`status`（POSTED/REVERSED）、`reversal_of`、`created_at`、`version`，唯一 `(source_type, source_id)`
- `journal_line`：`line_id`、`entry_id`、`line_no`、`account_code`、`direction`（DR/CR）、`amount`，唯一 `(entry_id, line_no)`

### 3.2 科目表种子

| 科目 | 名称 | 类型 |
|---|---|---|
| 1000 | Cash | ASSET |
| 1100 | Card Receivables | ASSET |
| 2000 | Customer Deposits | LIABILITY |
| 2100 | Merchant Payable | LIABILITY |
| 2200 | Other Payable | LIABILITY |
| 4000 | Fee Income | REVENUE |
| 4100 | Interest Income | REVENUE |
| 5000 | Interest Expense | EXPENSE |
| 9000 | Suspense | ASSET |

期间种子：`2026-09` CLOSED，`2026-10` OPEN，`2026-11` OPEN。

### 3.3 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/ledger-accounts` | 科目表 |
| GET | `/api/v1/ledger-accounts/{code}` | 科目详情 |
| POST | `/api/v1/journal-entries` | 记日记账，`(sourceType, sourceId)` 幂等 |
| GET | `/api/v1/journal-entries/{entryId}` | 详情 |
| GET | `/api/v1/journal-entries?page=&size=&sourceId=` | 分页 |
| POST | `/api/v1/journal-entries/{entryId}/reversal` | 冲正 |
| GET | `/api/v1/trial-balance?period=2026-10` | 试算平衡 |
| GET | `/api/v1/accounting-periods` | 期间列表 |
| POST | `/api/v1/accounting-periods/{period}/close` | 关账 |

记账请求：

```json
{"sourceType":"POSTING_BATCH","sourceId":"b-1","description":"Card purchase",
 "postedAt":"2026-10-26T04:00:00","currency":"USD",
 "lines":[{"accountCode":"1100","direction":"DR","amount":25.00},
          {"accountCode":"2100","direction":"CR","amount":25.00}]}
```

规则：

- 至少 2 行；金额 > 0；借贷合计相等，否则 409 `UNBALANCED_JOURNAL`。
- 科目不存在 404 `LEDGER_ACCOUNT_NOT_FOUND`；停用 409 `LEDGER_ACCOUNT_INACTIVE`。
- 期间不存在 404 `PERIOD_NOT_FOUND`；已关账 409 `PERIOD_CLOSED`。
- 幂等：已存在相同 `(sourceType, sourceId)` 且内容一致返回原分录；内容不一致 409 `JOURNAL_SOURCE_CONFLICT`。
- 冲正：生成 `sourceType=REVERSAL, sourceId=原 entryId`，方向互换；原分录标记 REVERSED；重复冲正返回已有冲正分录。
- 试算平衡：按期间过滤 `posted_at`，汇总每个科目 DR/CR 合计与净额，返回 `totalDebit/totalCredit/balanced/items`。

## 4. 前台

- 账户视图：展示产品、冻结合计、可用额度；支持创建/释放 hold。
- 新页面 `#/postings`（业务）：类型选择、账户、金额、幂等键、提交；最近批次列表；冲正按钮。
- 新页面 `#/ledger`（管理）：科目表、日记账列表、试算平衡、关账按钮；手工记账表单（2 行）。
- 导航与首页入口同步；`web-ui` 代理增加过账/总账路径。

## 5. 验收

1. `POST /posting-batches` 成功：账户余额按 INCREASE/DECREASE 变化，批次与 legs 落库。
2. 重复幂等键返回同一批次；金额不同返回 409。
3. 冲正后账户余额恢复，原批次 REVERSED，冲正批次 direction 互换。
4. 总账收到同批次分录；试算平衡借贷相等。
5. 关账后该期间记账返回 `PERIOD_CLOSED`。
6. 前台可完成：账户冻结 → 过账 → 查看批次 → 冲正 → 查看总账/试算平衡。
7. 全仓测试全绿，Docker Compose 可启动（A2 时 10 个容器，A1.4 后 11 个）。
