# 白金卡额外 0.5% 消费返现：实现设计

| 项 | 内容 |
|----|------|
| 编号 | 18 |
| 日期 | 2026-11-13 |
| 状态 | 已实现（单测通过，待评审） |
| 关联需求 | `docs/requirements/REQ-2026-005-platinum-extra-cashback.md`（REQ-dcf89122，PRD v1.0） |
| 关联 ADR | ADR-013（营销独立上下文与贷记入账）、ADR-009（同步集成）、ADR-007（鉴权缺口） |
| 代码版本 | 分支 `codex/carddemo-req-dcf89122-605b-4263-92f6-9a893c967a7e-f53dcd95`，基线 `1ddd92c` |

## 1. 目标与范围

实现 PRD 的 7 条功能需求。营销侧（活动、费率、窗口、门槛、上限、预算、返现计算与贷记）在基线 `main` 已完整实现，本次只新增一条活动配置；唯一的代码改动是 `transaction-service` 的消费类交易过滤（PRD D-07）。

不在本次范围：退款/拒付回收（D-06）、追溯补发（D-08）、营销接口鉴权（D-10）。

## 2. 变更总览

| 位置 | 变更 | 对应需求 |
|------|------|---------|
| `services/transaction-service/.../application/PostingService.java` | 新增 `marketing.reward-type-codes` 配置与消费类过滤：非消费类交易入账后不调用返现评估 | REQ-CB-02、D-07 |
| `services/transaction-service/src/main/resources/application.yml` | 新增 `marketing.reward-type-codes: ${MARKETING_REWARD_TYPE_CODES:01}` | 同上 |
| `services/transaction-service/src/test/.../PostingServiceTest.java` | 新增 `nonPurchasePostingSkipsMarketingEvaluation` | REQ-CB-02 |
| 运营配置（非代码） | 新增活动 `PLATINUM_EXTRA_0_5_3M`：费率 0.005、窗口 2026-11-13～2027-02-12、`PLATINUM`、门槛 50.00、单笔上限 30.00、客户上限 300.00、预算 50000.00、免报名 | REQ-CB-01、REQ-CB-03～06 |

## 3. 详细设计

### 3.1 消费类交易过滤（唯一代码改动）

**问题**：基线 `PostingService.post` 在每笔入账成功后无条件调用 `evaluateRewards`，费用、利息、还款等非消费类交易也会进入营销评估，从而获得 0.5% 返现，与「消费返现」的口径不符（PRD R-07、RC-3）。

**方案**：在 `evaluateRewards` 入口按交易类型过滤。

```text
post(commands)
  └─ 入账成功（余额、transaction、daily_transaction 已落库）
       └─ evaluateRewards(record, card)
            ├─ marketing.enabled = false      → 返回（基线行为）
            ├─ typeCode ∉ reward-type-codes   → 返回（新增）
            └─ marketingClient.evaluateRewards(...)  → 失败只 WARN，不回滚入账（基线行为）
```

- 类型集合来自配置 `marketing.reward-type-codes`（默认 `01`），逗号分隔，构造时解析为不可变集合，运行时无额外开销。
- 过滤发生在调用下游**之前**，因此既不会产生返现记录，也不增加营销服务的无效流量。
- 过滤发生在资金动作**之后**：入账、账户余额、日交易记录与交易记录均已完成，过滤不影响资金事实（BR-CB-11）。
- 选择配置而非硬编码常量：RC-3（是否存在 `01` 之外的消费类编码）仍未确认，配置化可在不改代码的前提下调整，回退也只需改环境变量。

**边界与异常**：

| 场景 | 行为 |
|------|------|
| `typeCode = 01`（Purchase） | 正常评估（既有测试覆盖） |
| `typeCode = 02`（Payment）/`05`（Refund）等 | 跳过评估，入账与余额照常（新增测试覆盖） |
| 配置为多个类型（如 `01,05`） | 两类均参与评估 |
| 配置为空字符串 | 集合为空，等同关闭返现评估（保留 `marketing.enabled` 总开关） |
| 营销调用失败/超时 | 维持基线：只记 WARN，不回滚入账 |

### 3.2 活动配置

按 PRD D-01～D-05 由运营通过 `POST /api/v1/campaigns` 创建并发布，字段取值见 PRD §7 示例。营销侧不需要任何代码改动：

- 返现额 = `min(金额 × 0.005, 30.00, 客户剩余额度, 活动剩余预算)`，HALF_UP 2 位，由 `RewardCalculator` 计算；
- 与既有白金卡 5% 活动并行：`RewardService.evaluate` 遍历全部 `ACTIVE` 活动，`grants` 返回多条，各自独立上限与预算；
- 返现经 `account-service` 贷记冲减欠款，`creditId = rewardId` 幂等。

### 3.3 不影响资金事实

营销评估仍在入账事务之后执行，异常被捕获并记 WARN。新增的过滤分支在任何下游调用之前返回，不引入新的失败面。

## 4. 需求覆盖矩阵

| 需求 | 实现位置 | 验证 |
|------|---------|------|
| REQ-CB-01 活动配置与发布 | 基线 `marketing-service`（活动 CRUD/发布）+ 运营配置 | 手工/接口验证（配置类需求，无代码改动） |
| REQ-CB-02 评估与多活动叠加 | `PostingService.evaluateRewards`（过滤）+ 基线 `RewardService.evaluate` | `PostingServiceTest.postedTransactionEvaluatesMarketingRewards`、`nonPurchasePostingSkipsMarketingEvaluation` |
| REQ-CB-03 入账与幂等重试 | 基线 `RewardService` + account 贷记 | 基线用例；本次未改动 |
| REQ-CB-04 上限与预算 | 基线 `RewardCalculator`、`RewardService` | 基线用例；本次未改动 |
| REQ-CB-05 客户可见性 | 基线接口与页面 | 本次未改动 |
| REQ-CB-06 活动效果 | 基线 performance 接口 | 本次未改动 |
| REQ-CB-07 退款不回收 | 无实现（D-06 决定） | 记录为已知限制，见 PRD 12.3 RC-2 |

## 5. 测试

| 用例 | 类型 | 断言 |
|------|------|------|
| `postedTransactionEvaluatesMarketingRewards` | 正常 | 消费类入账后调用评估，命令字段与交易一致 |
| `nonPurchasePostingSkipsMarketingEvaluation` | 边界/分类 | `typeCode=02` 入账成功、余额 `02/0002` 增加 75.00，`marketingClient` 从未被调用 |
| `marketingFailureDoesNotRollBackPosting` | 异常 | 营销不可用时入账成功、状态 `POSTED` |
| `duplicateTransactionIdIsIdempotent` 等 | 幂等/异常 | 基线用例保持通过 |
| `RewardServiceTest.stacksPremiumAndExtraHalfPercentCampaigns` | 叠加/正常 | 5% 与额外 0.5% 两个 ACTIVE 活动命中同一 1000.00 交易 → 2 条 grant（30.00、5.00），预算各自消耗，`credit` 调用 2 次 |
| `RewardServiceTest.grantsExtraHalfPercentWithinWindowAndCapsSingleTransaction` | 费率/边界 | 0.005 费率：120.00→0.60、6000.00→30.00 封顶；2027-02-13 交易 → `OUTSIDE_WINDOW` |
| `MarketingControllerTest.evaluationRejectsZeroAmount` | 接口/异常 | `amount=0.00` → 400 `INVALID_AMOUNT` |

执行结果：`mvn -B test` 在 `services/transaction-service` 共 66 个用例、`services/marketing-service` 共 45 个用例全部通过（`PostingServiceTest` 7 个、`RewardServiceTest` 11 个、`MarketingControllerTest` 8 个）。跨服务端到端仍受限于本地无容器环境，见 `docs/verification/` 下的验证报告。

## 6. 部署与回退

| 项 | 说明 |
|----|------|
| 新增配置 | 环境变量 `MARKETING_REWARD_TYPE_CODES`（默认 `01`），无破坏性变更 |
| 上线顺序 | 先发布 transaction-service（过滤生效），再由运营创建并发布活动；未发布活动时无返现 |
| 回退 | 结束活动（`POST /campaigns/{id}/end`）即停止新增返现；如需完全关闭，置 `MARKETING_ENABLED=false`；过滤本身不影响入账，无需回退代码 |
| 数据 | 无表结构变更 |

## 7. 已知限制

1. RC-1：D-01～D-10 由交付人授权决定，业务、财务、合规会签未完成，投产前必须签认。
2. RC-2（PRD D-06）：退款、撤销、拒付不回收返现，存在资金多返风险。
3. RC-3：消费类类型集合按 `01`（Purchase）判定，若存在其他消费类编码需调整 `MARKETING_REWARD_TYPE_CODES`。
4. ADR-007（PRD D-10）：营销接口无接口级鉴权，仅限本机/环回地址访问。
5. 预算与上限仅单实例保护（`docs/design/17` §8），多实例部署需行锁/条件更新。
