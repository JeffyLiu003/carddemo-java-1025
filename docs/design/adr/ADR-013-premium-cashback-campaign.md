# ADR-013: 高端卡分期返现采用独立营销上下文

| 项 | 内容 |
|----|------|
| 编号 | ADR-013 |
| 日期 | 2026-10-26 |
| 状态 | 已接受 |
| 关联需求 | `docs/design/17-premium-cashback-campaign.md` |

## 背景

旧营销能力覆盖活动、报名、积分、返现、预算与效果，范围较宽且已被移除。
新需求聚焦：白金卡等价值客户、未来 N 个月（默认 3 个月）消费返现。
需要决定：

1. 营销是否重新作为独立服务；
2. 返现如何入账；
3. 活动期限与产品圈选如何建模；
4. 营销失败是否影响交易入账。

## 候选方案

| 方案 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| A：营销逻辑内嵌 transaction-service | 入账时直接算返现并调账户 | 少一个服务 | 交易服务变重；活动配置/运营接口难独立演进 |
| B：独立 marketing-service + 账户贷记（采用） | 交易入账后同步评估；营销服务管理活动/报名/奖励；返现调 account 贷记 | 边界清晰；资金事实与营销权益分离；可独立运营 | 多一跳；最终一致 |
| C：MQ 事件驱动 | 入账发事件，营销异步消费 | 解耦、可重放 | 需要 MQ 与消费治理，本期成本高 |

## 决策

采用方案 B，并约束：

1. marketing-service 只负责活动、报名、返现计算与奖励记录；账户余额仍由 account-service 权威。
2. transaction-service 入账成功后调用 `POST /reward-evaluations`；失败只 WARN，不回滚入账。
3. 返现通过 `POST /accounts/{id}/credits` 入账，`creditId` 幂等；余额可负（溢缴）。
4. 活动以 `startDate + durationMonths` 定义期限；按产品等级（PLATINUM/GOLD）圈选客户。
5. 奖励以 `(campaignId, transactionId)` 幂等；预算/客户上限在营销库校验。
6. 本期不恢复积分、A/B、复杂客群 DSL；后续可在同一上下文扩展。

## 后果

- 营销不可用时交易仍成功，返现可能延迟；PENDING 可人工重试。
- 预算/上限目前为单实例保护；多实例需行锁/条件更新（已列为风险）。
- 账户贷记接口是通用能力，可被其他权益场景复用。
- 先前的营销设计与决策不再恢复；新设计以本 ADR 与 17 号文档为准。

## 证据

- `docs/design/17-premium-cashback-campaign.md`
- `services/marketing-service/src/main/java/com/carddemo/marketing/domain/service/RewardCalculator.java`
- `services/account-service/src/main/java/com/carddemo/account/application/AccountApplicationService.java`
- `services/transaction-service/src/main/java/com/carddemo/transaction/application/PostingService.java`
