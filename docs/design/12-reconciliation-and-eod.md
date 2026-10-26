# 12 钱路径切片 A1.4：对账与 EOD

> 上游：`11-money-path-slice-a2.md`（A1.4）
> 状态：已实现（A1.4）

## 1. 目标

验证"业务过账批次"与"总账日记账"两条链路完整且金额一致，并把差异落成可跟踪、可处理的记录；
提供 EOD 作业入口，为后续计息、账单、总账结转预留编排位。

```
transaction posting_batch ──┐
                            ├─ 对账（按业务日期）→ reconciliation_run / item → 差异登记/处理
ledger journal_entry ───────┘
                            └─ 试算平衡（按期间）→ balanced?
EOD run → 触发对账 → 记录作业结果
```

## 2. 对账口径

- **业务日期**：`posting_batch.created_at` 与 `journal_entry.posted_at` 的日期部分。
- **匹配键**：`posting_batch.batch_id` ↔ `journal_entry.source_id`（仅 `sourceType=POSTING_BATCH`）。
- **金额**：批次金额 = 日记账借方合计 = 贷方合计。
- **孤儿**：有日记账无批次（`MISSING_BATCH`）；有批次无日记账（`MISSING_JOURNAL`）。
- **试算平衡**：期间 `Σ借 = Σ贷`，否则 `UNBALANCED`。
- 手工日记账（`sourceType=MANUAL`）不参与批次匹配，不影响批次对账结论。

## 3. 服务与表（reconciliation-service，:8089，DB `carddemo_reconciliation`）

### 3.1 `reconciliation_run`

`run_id`、`business_date`、`period`、`status`（RUNNING/COMPLETED/FAILED）、`started_at`、`completed_at`、
`batch_count`、`batch_amount`、`journal_count`、`journal_debit_total`、`journal_credit_total`、
`match_count`、`mismatch_count`、`missing_journal_count`、`missing_batch_count`、`matched`、`error_message`、`version`。

### 3.2 `reconciliation_item`

`item_id`、`run_id`、`check_type`（POSTING_VS_JOURNAL/TRIAL_BALANCE）、`reference_id`、
`expected_amount`、`actual_amount`、`difference`、`status`
（MATCH/MISSING_JOURNAL/MISSING_BATCH/AMOUNT_MISMATCH/UNBALANCED）、`resolved`、`resolution_note`、`detail`、`version`。

### 3.3 `eod_run`

`run_id`、`business_date`、`status`（RUNNING/COMPLETED/FAILED）、`reconciliation_run_id`、
`started_at`、`completed_at`、`message`、`version`。

## 4. API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/reconciliation-runs` | 按 `businessDate` 执行对账，201 |
| GET | `/api/v1/reconciliation-runs/{runId}` | 运行详情 + items |
| GET | `/api/v1/reconciliation-runs?page=&size=` | 运行历史 |
| GET | `/api/v1/reconciliation-runs/{runId}/items` | 差异明细 |
| POST | `/api/v1/reconciliation-items/{itemId}/resolve` | 登记差异处理说明 |
| POST | `/api/v1/eod-runs` | 执行 EOD（当前步骤：对账），201 |
| GET | `/api/v1/eod-runs/{runId}` | EOD 详情 |
| GET | `/api/v1/eod-runs?page=&size=` | EOD 历史 |

对账请求：

```json
{"businessDate":"2026-10-26"}
```

响应摘要：

```json
{"runId":"...","businessDate":"2026-10-26","period":"2026-10","status":"COMPLETED",
 "matched":true,"batchCount":2,"batchAmount":50.00,"journalCount":2,
 "matchCount":3,"mismatchCount":0,"missingJournalCount":0,"missingBatchCount":0,
 "items":[{"checkType":"POSTING_VS_JOURNAL","referenceId":"b-1","status":"MATCH",
           "expectedAmount":25.00,"actualAmount":25.00,"difference":0.00}]}
```

差异处理：

```json
{"note":"已确认总账同步延迟，手工补录"}
```

## 5. 依赖与失败语义

- transaction-service：分页读取 `/api/v1/posting-batches?page=&size=`，按业务日期过滤。
- ledger-service：分页读取 `/api/v1/journal-entries?page=&size=`，按记账日期过滤；
  读取 `/api/v1/trial-balance?period=YYYY-MM`。
- 下游不可用 → 运行置 FAILED，保留 `error_message`；接口返回 503 `DEPENDENCY_UNAVAILABLE`。
- 对账只读业务与总账，不修改任何账务数据；差异处理只更新对账记录。

## 6. 前台

- 管理菜单新增 `#/reconciliation`：
  - 业务日期 + "执行对账" / "执行 EOD"；
  - 对账历史（状态、是否匹配、批次/日记账数量、差异数）；
  - 运行详情（摘要 + items 表格 + 未匹配项"登记处理"）。
- 首页管理员入口同步；代理新增对账/EOD 路径。

## 7. 验收

1. 过账批次与总账日记账一一对应时，`matched=true`、`mismatchCount=0`。
2. 删除/缺失日记账（模拟）时出现 `MISSING_JOURNAL`；多出日记账时出现 `MISSING_BATCH`。
3. 金额不一致时出现 `AMOUNT_MISMATCH`，`difference` 正确。
4. 试算不平衡时出现 `UNBALANCED`，`matched=false`。
5. 差异项可登记处理说明，刷新后保留。
6. EOD 运行记录关联对账运行，成功/失败状态可查。
7. 前台可完成：执行对账 → 查看差异 → 登记处理 → 查看 EOD 历史。

## 8. 后续

- 账户余额 vs 总账 1100 的对账需要按账户的子账映射（当前总账只到科目级）。
- EOD 作业图扩展：计息 → 费用 → 账单 → 逾期滚动 → 总账结转 → 对账 → 报表。
