# ADR-010: 过账引擎与总账的职责分离

| 项 | 内容 |
|----|------|
| 编号 | ADR-010 |
| 日期 | 2026-10-26 |
| 状态 | 已接受 |
| 关联需求 | `docs/design/11-money-path-slice-a2.md`、A1.2/A1.3 |

## 背景

A1.2 引入过账批次（业务交易 → 借贷分录），A1.3 引入总账（科目、日记账、试算平衡）。
需要决定：

1. 过账分录与总账日记账是否放在同一个服务/数据库；
2. 账户余额变更、批次落库、总账记账三者的失败边界；
3. 冲正如何保证两侧一致且可审计。

约束：现有系统无 MQ；账户余额在 account-service，交易在 transaction-service；演示环境要求
一条命令启动、可重复演示。

## 候选方案

| 方案 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| A：合并到 transaction-service | 过账批次直接写总账科目/分录 | 单库事务，强一致；实现最简 | 总账与交易耦合；无法独立关账/审计；违背限界上下文 |
| B：独立 ledger-service + 同步调用（失败告警） | transaction 先改账户、落批次，再 HTTP 写总账；总账幂等 | 边界清晰；可独立试算/关账；失败不影响资金事实 | 最终一致；总账可能短暂缺失，需要补偿/对账 |
| C：Outbox + MQ | 批次落库时写 outbox，ledger 订阅事件 | 彻底解耦；可重放；吞吐高 | 需要 MQ 与消费治理；本期成本高 |

## 量化权衡

| 方案 | 实现成本 | 一致性 | 可审计性 | 运维复杂度 | 风险 |
|---|---|---|---|---|---|
| A | 低 | 强 | 中（交易与总账混在一起） | 低 | 总账演进受限 |
| B | 中 | 最终（秒级） | 高（科目/期间/分录独立） | 中 | 总账写入失败需告警与补偿 |
| C | 高 | 最终（可重放） | 高 | 高 | 消息积压/重复消费 |

## 决策

采用方案 B，并落实：

1. **职责分离**：transaction-service 拥有业务交易、过账类型/规则/批次；ledger-service 拥有科目表、
   日记账、期间。两边各自一个数据库，禁止跨库 JOIN。
2. **调用顺序**：校验 → 调 account-service 改余额 → 本地保存批次与 legs → 调 ledger 写日记账。
   总账调用异常只 `WARN`，批次保留 `ledger_entry_id = null`。
3. **幂等**：过账批次以 `idempotencyKey` 唯一；总账以 `(sourceType, sourceId)` 唯一；
   冲正批次/冲正分录各自使用确定性业务键，重复请求返回已有记录。
4. **冲正**：不删除原记录。过账侧生成方向互换的冲正批次并标记原批次 `REVERSED`；
   总账侧生成 `sourceType=REVERSAL` 的反向分录并标记原分录 `REVERSED`。
5. **期间关账**：总账只允许在 OPEN 期间记账；关账不回压交易服务（见后果）。

## 理由

1. 资金事实（账户余额、过账批次）与会计投影（总账）失败域不同，合并会让报表/关账阻塞交易。
2. 总账需要独立演进（多币种、合并报表、审计），独立服务边界更稳。
3. 同步调用与既有跨服务一致性策略一致，不引入 MQ 即可演示完整链路；幂等键为未来 Outbox 重放预留。

## 后果（影响与回退）

- **最终一致**：总账可能短暂缺少某笔分录；`ledger_entry_id` 为空可作为补偿作业的扫描条件。
- **关账不阻塞交易**：已关账期间仍可能发生业务过账，此时总账同步失败并告警；生产化需增加
  "关账前冻结交易"或"跨期自动转入下一期间"策略。
- **试算平衡口径**：按期间发生额汇总，包含原分录与冲正分录；不含期初余额。
- **回退**：关闭 `ledger.sync-enabled` 即停止总账同步；交易与账户不受影响。若改为方案 C，
  只需把同步调用替换为 outbox 发布，接口与幂等键不变。

## 证据

- `docs/design/11-money-path-slice-a2.md` §2、§3
- `docs/design/03-service-decomposition.md` §3、§5
- `services/transaction-service/src/main/java/com/carddemo/transaction/application/PostingBatchService.java`
- `services/ledger-service/src/main/java/com/carddemo/ledger/application/JournalEntryApplicationService.java`
