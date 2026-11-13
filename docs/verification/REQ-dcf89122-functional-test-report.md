# 功能测试报告 · REQ-dcf89122 白金卡额外 0.5% 消费返现

| 项 | 内容 |
|----|------|
| 代码版本 | `1a66fff39d5f6dd06b78ff868ae7347468873047`（分支 `codex/carddemo-req-dcf89122-…`） |
| 测试依据 | PRD v1.0 §10 验收标准 US-CB-001～011；软件设计 `docs/design/18` |
| 用例来源 | **PRD 派生**。企业测试资产插件（`test-cases` 输入）未登记，本次用例由 PRD 第 10 节直接派生，非企业测试资产 |
| 执行时间 | 2026-11-13 18:36 +08:00（根工程）/ 18:42 +08:00（分模块复核） |
| 环境 | JDK 21.0.12.1（Temurin）、Maven 3.9.16、Windows Server；**无 Docker，无跨服务运行时** |
| 结论 | **未通过**：11 条标准中 4 条通过、5 条部分覆盖、2 条无任何断言 |

## 1. 实际执行的命令与结果

| # | 命令 | 范围 | 结果 |
|---|------|------|------|
| R1 | `mvn -B test`（仓库根，29 模块） | 全仓回归 | 28 模块通过；`authorization-service` 1 个用例失败（见 §4），`BUILD FAILURE`，exit 1 |
| R2 | `mvn -B test`（`services/marketing-service`） | 返现域与接口 | 42 用例通过，0 失败，0 跳过，exit 0 |
| R3 | `mvn -B test`（`services/transaction-service`） | 过账与消费类过滤 | 66 用例通过，0 失败，0 跳过，exit 0 |
| R4 | `mvn -B test`（`services/account-service`，含在 R1 内） | 贷记与幂等 | 全部通过，含 `creditReducesBalanceAndIsIdempotentPerCreditId` |

日志：`…\3dcf4860-…mvn-root-test.log`（R1，SHA256 待核）；R2/R3 结果见同一日志及 `…mvn-test.log`。

## 2. 验收标准覆盖矩阵（基于实际测试源码核对）

| 用例 | 状态 | 断言测试 | 未覆盖部分 |
|------|------|---------|-----------|
| US-CB-001 消费 120.00 → 返现 0.60、余额 2550.75→2550.15 | **partial** | `RewardServiceTest.grantsCashbackAndReservesBudget:97`；`AccountControllerTest.creditReducesBalanceAndIsIdempotentPerCreditId:103` | 无 0.005 费率与 120.00→0.60 断言；无跨服务余额联动；PRD 余额基数与种子数据不一致（见 §4 ISS-02） |
| US-CB-002 窗口外跳过 | 通过 | `RewardServiceTest.skipsCampaignsThatDoNotApply:142`（`:157` 断言 `OUTSIDE_WINDOW`）；`CampaignTest.matchesProductCodesAndTransactionWindow:126` | 未用真实活动窗口 2026-11-13～2027-02-12 首末日数据 |
| US-CB-003 非白金卡不符合产品 | 通过 | 同上（`:154-155` 断言 `PRODUCT_NOT_ELIGIBLE`） | — |
| US-CB-004 重复评估不新增 | 通过 | `RewardServiceTest.evaluationIsIdempotentPerCampaignAndTransaction:163` | — |
| US-CB-005 客户上限 300.00 | 通过 | `RewardServiceTest.capsCashbackAtCustomerLimitAndThenSkips:180` | 用 40.00 代替 300.00 上限，金额组合与 PRD 用例不同 |
| US-CB-006 预算耗尽 | 通过 | `RewardServiceTest.skipsWhenCampaignBudgetIsExhausted:197`；`RewardCalculatorTest.capsCashbackAtCustomerAndBudgetRemaining:35` | — |
| US-CB-007 贷记失败 PENDING 与重试 | **partial** | `RewardServiceTest.keepsPendingWhenCreditFailsAndRetryIsIdempotent:212`；`MarketingControllerTest.evaluationRetryRewardsPerformanceAndSummary:277` | 失败为 Mockito 模拟而非真停服务；「仅贷记一次」无 `verify(times(1))` |
| US-CB-008 金额为 0 → 400 `INVALID_AMOUNT` | **未覆盖** | 无 | 全仓无该用例；`EvaluateRewardRequest.amount` 仅 `@NotNull` |
| US-CB-009 两个 ACTIVE 活动叠加 | **未覆盖** | 无 | 所有评估断言均为单条 grant；D-01「独立叠加」无自动化证据 |
| US-CB-010 门槛 50.00 | 通过 | `RewardServiceTest.skipsTransactionBelowMinimumAmount:127` | 用 40.00，未测 49.99/50.00 边界 |
| US-CB-011 非消费类不评估 | 通过 | `PostingServiceTest.nonPurchasePostingSkipsMarketingEvaluation:122`（`verify(marketingClient, never())`） | — |

补充：`reason` 码断言覆盖 `OUTSIDE_WINDOW` / `PRODUCT_NOT_ELIGIBLE` / `ALREADY_EVALUATED` / `CUSTOMER_CAP_REACHED` / `BUDGET_EXHAUSTED` / `AMOUNT_TOO_LOW`，缺 `INVALID_AMOUNT`。

## 3. 未执行的范围

1. **跨服务端到端验证**（真实 HTTP：过账 → 评估 → 贷记 → 余额）：环境无 Docker，且无法同时拉起 6 个服务；PRD US-CB-001 的余额变化与 US-CB-007 的容器停用/恢复因此只能依赖服务层替代证据。
2. **活动配置本身的运行期验证**：本次未在运行环境创建 `PLATINUM_EXTRA_0_5_3M`（未对运行环境做写操作），活动参数正确性只做了代码/文档层面核对。
3. **`scripts/smoke-test.sh`**：脚本只 print 不 assert，且需要运行环境，未执行。

## 4. 环境与既有问题（非本次变更引入）

- `authorization-service`：`AuthorizationControllerTest.purgesExpiredDetailsAndReleasesCreditBalance:266` 期望 `creditBalanceReleased=100.00` 失败。`git diff 1ddd92c..HEAD -- services/authorization-service` 为空，该模块未被本次提交触碰；属既有失败，不纳入本需求结论，但会污染「全仓绿」的判断。
- 种子数据与 PRD 不一致：`services/account-service/src/main/resources/db/migration/V2__account_seed.sql:15` 白金账户余额为 2450.75（PRD 写 2550.75）；`services/marketing-service/src/main/resources/db/migration/V2__marketing_seed.sql:7-10` 只有 5% 活动（2026-10-01～2026-12-31），无 0.005 活动种子。
