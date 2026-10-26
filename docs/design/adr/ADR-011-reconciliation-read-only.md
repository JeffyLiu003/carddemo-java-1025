# ADR-011: 对账只读与差异处理边界

| 项 | 内容 |
|----|------|
| 编号 | ADR-011 |
| 日期 | 2026-10-26 |
| 状态 | 已接受 |
| 关联需求 | `docs/design/12-reconciliation-and-eod.md`、A1.4 |

## 背景

A1.4 引入对账：比较过账批次与总账日记账，识别缺失、金额不符、孤儿分录与试算不平衡。
对账发现差异后，系统可以：

1. 自动修改业务/总账数据使两边一致；
2. 只记录差异，由人工确认后另行调账；
3. 直接阻断交易。

需要决定对账服务的写权限边界，以及 EOD 的职责。

## 候选方案

| 方案 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| A：对账可自动调账 | 发现差异后写反向分录/改余额 | 自动化程度高 | 对账错误会二次破坏账务；审计不可接受 |
| B：只读 + 差异登记 + 人工处理（采用） | 对账只读；差异落库，可登记说明；调账走正常过账/手工分录 | 审计清晰；失败不影响账务 | 需要人工闭环；差异处理与调账是两步 |
| C：对账阻断交易 | 差异未清零前禁止新交易 | 强一致 | 可用性差；历史差异会长期阻塞业务 |

## 决策

采用方案 B：

1. `reconciliation-service` 只通过 GET 读取 transaction 的过账批次、ledger 的日记账与试算平衡，
   不写任何账务数据。
2. 差异写入 `reconciliation_item`，状态为 `MISSING_JOURNAL`、`MISSING_BATCH`、`AMOUNT_MISMATCH`、`UNBALANCED`；
   `resolve` 只更新 `resolved` 与 `resolution_note`，不触发调账。
3. 真正的调账必须走 transaction 的过账批次或 ledger 的手工日记账，保留完整审计链。
4. EOD 作为作业入口，当前只编排对账；不自动关账、不改余额。
5. 对账范围当前为“批次 ↔ 日记账 + 试算平衡”；账户余额 vs 总账 1100 需要按账户子账，暂不纳入。

## 后果

- 差异需要人工跟进；系统提供“已处理”标记与说明，便于审计。
- 对账失败不阻塞交易，符合“资金事实优先”的原则。
- 后续若引入自动调账，应以新的过账批次/日记账实现，而不是直接改余额。
- 对账拉取采用分页读取（每页 500、最多 40 页）；数据量增大后应下推到服务端按日期过滤。

## 证据

- `docs/design/12-reconciliation-and-eod.md` §2、§5
- `docs/design/adr/ADR-010-ledger-and-posting-separation.md`
- `services/reconciliation-service/src/main/java/com/carddemo/reconciliation/application/ReconciliationService.java`
