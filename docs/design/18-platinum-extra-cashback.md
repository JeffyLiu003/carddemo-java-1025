# 18. 白金卡消费额外 0.5% 返现 — 方案设计

> 需求：REQ-01af1cdb-c7dd-4434-8cec-ac9547e12b79（需求说明书 `docs/requirements/REQ-2026-005-platinum-extra-cashback.md`，REQ-CB-01～07）
> 前置能力：`docs/design/17-premium-cashback-campaign.md`、`docs/design/adr/ADR-013-premium-cashback-campaign.md`
> 状态：设计完成，代码与单测已按本文实现（2026-11-02）；生产发布前的活动参数仍需业务与运营复核

## 1. 目标与范围

**目标**：活动期内，白金卡客户消费在既有返现之外「额外」获得 0.5% 返现。

**范围内**

| 项 | 说明 |
|----|------|
| 活动配置 | 新增一个独立的白金卡额外返现活动（V3 种子迁移） |
| 评估链路 | 复用既有逐活动评估（产品资格 → 窗口 → 报名 → 门槛 → 上限 → 预算 → 幂等 → 入账） |
| 可观测 | 复用 `reward_grant` 与活动效果/看板接口，无需新增字段 |

**范围外**

| 项 | 原因 |
|----|------|
| 退款/拒付返现冲正 | PRD Q8 本期结论为「不实现」，作为已知限制记录（§7 R1） |
| account-service 改造 | 复用既有 `credit` 记账接口，入账语义不变 |
| 前端活动页改造 | 现有活动管理页可直接展示新活动（配置驱动） |

## 2. 关键决策：以独立活动承载「额外 0.5%」

| 候选方案 | 改动面 | 业务语义 | 可回滚性 | 运营灵活性 | 主要风险 |
|---------|--------|---------|---------|-----------|---------|
| **A. 新增独立活动（选用）** | 1 个迁移文件 + 测试；领域代码 0 改动 | 「额外」= 与既有 5% 活动并存，同一交易两笔返现，合计 5.5% | 高：将新活动置 PAUSED/ENDED 即回到原状 | 高：独立费率、上限、预算、报名、窗口 | 双发（两笔 grant）、预算与客户上限各占一份 |
| B. 调整既有活动费率 0.05 → 0.055 | 1 个数据变更；无代码 | 返现口径变成「合计 5.5% 单一活动」 | 中：费率可直接改回，但已发生返现不可追改 | 中：与主体活动同生共死，无法单独停投 | 费率语义与历史活动描述不一致，审计需解释 |
| C. 在 `RewardCalculator` 内硬编码额外费率 | 领域代码 + 单测 | 依赖代码分支，脱离活动配置 | 低：需发版回滚 | 低：任何参数调整都要发版 | 违背配置驱动与 PC-01～PC-10 的既有设计 |

**选择 A**，理由：

1. 需求措辞是「额外增加」，两笔独立返现是最忠实、最易审计的表达；
2. 零领域代码改动，风险集中在配置与数据，回滚只需变更活动状态；
3. 额外活动可独立暂停或提前结束，不影响既有 5% 活动；
4. 与 ADR-013 的「每活动独立评估、幂等键按活动维度」一致。

**代价**（已被接受）：同一交易产生两笔 `reward_grant`，对账与客服口径需说明「合计 5.5%」；预算与客户上限按活动各自计算（§7 R2/R4）。

## 3. 配置明细（V3 种子活动）

| 字段 | 取值 | 来源 |
|------|------|------|
| code | `PLATINUM_EXTRA_0P5_CASHBACK` | 本设计 |
| name / description | 白金卡消费额外 0.5% 返现 | 本设计 |
| status | `ACTIVE` | 本设计（生效即参与评估） |
| eligible_product_codes | `PLATINUM` | 原始需求（白金卡） |
| cashback_rate | `0.005000` | 原始需求（额外 0.5%） |
| start_date / duration_months / end_date | `2026-11-01` / `3` / `2027-01-31` | 开发者决策 2026-11-02（固定窗口，PC-01） |
| min_transaction_amount | `50.00` | 沿用 `PLATINUM_3M_CASHBACK`（开发者决策，Q3/Q6） |
| max_cashback_per_transaction | `30.00` | 同上（Q4） |
| max_cashback_per_customer | `300.00` | 同上（Q4） |
| budget | `50000.00` | 同上（Q4） |
| enrollment_required | `TRUE` | 同上（Q5） |

> 上述「开发者决策」均在 TUI 会话中由开发者选定（2026-11-02 17:0x），尚未登记到 Teams `agreement.confirmations`；PRD 12.3 节对应 Q1/Q2/Q4/Q5 的最终业务确认与责任方签字仍待完成。

## 4. 评估链路与叠加行为

```
交易事件 ──► RewardService.evaluate
              ├─ 查询 ACTIVE 活动（按产品资格过滤）
              ├─ 逐活动：窗口(PC-03) → 报名(PC-05) → 门槛(PC-06)
              │            → 客户累计上限(PC-08) → 幂等(PC-10) → 预算预占(PC-09)
              ├─ 计算：amount × rate，HALF_UP 保留 2 位，单笔封顶(PC-07)
              └─ 每活动各建 1 条 reward_grant（PENDING）→ account-service credit → GRANTED
```

| 场景 | 主体活动 5% | 额外活动 0.5% | 客户合计 |
|------|------------|--------------|---------|
| 消费 120.00 | 6.00 | 0.60 | 6.60 |
| 消费 1000.00（触及单笔上限 30.00） | 30.00 | 5.00 | 35.00 |
| 消费 40.00（低于门槛 50.00） | 跳过 `AMOUNT_TOO_LOW` | 跳过 `AMOUNT_TOO_LOW` | 0 |
| 重复提交同一 `transactionId` | `ALREADY_EVALUATED` | `ALREADY_EVALUATED` | 0（不重复入账） |

幂等键为 `(campaign_id, transaction_id)`（`uk_reward_grant_campaign_transaction`），因此两活动各自幂等，互不吞并。

## 5. 影响分析

| 文件 | 变更 | 说明 |
|------|------|------|
| `services/marketing-service/src/main/resources/db/migration/V3__premium_extra_cashback_seed.sql` | 新增 | 插入额外返现活动（ACTIVE） |
| `services/marketing-service/src/test/java/com/carddemo/marketing/application/MarketingSeedTest.java` | 新增 1 个用例 | 校验 V3 活动字段口径 |
| `services/marketing-service/src/test/java/com/carddemo/marketing/application/RewardServiceTest.java` | 新增 2 个用例 + 1 个测试夹具方法 | 叠加发放、门槛与各自上限 |

领域模型、应用服务、REST 接口、account-service 均未改动。

## 6. 测试设计与结果

执行：`mvn -B -s D:\tools\settings.xml -pl services/marketing-service test`（JDK 21.0.12.1，Maven 3.9.16，H2 in-memory + Flyway V1～V3）

| 用例 | 断言要点 | 结果 |
|------|---------|------|
| `MarketingSeedTest.seedsPlatinumExtraCashbackCampaign` | ACTIVE、2026-11-01～2027-01-31、PLATINUM、0.005、50.00/30.00/300.00/50000.00、需报名 | 通过 |
| `RewardServiceTest.stacksExtraCashbackOnTopOfBaseCampaign` | 120.00 → 6.00 + 0.60；两笔 GRANTED 且各自 credit；预算各预占；重复提交两活动均 `ALREADY_EVALUATED` 且只入账 2 次 | 通过 |
| `RewardServiceTest.extraCashbackKeepsMinimumAmountAndOwnCaps` | 40.00 两活动都 `AMOUNT_TOO_LOW`；1000.00 → 30.00 + 5.00，合计 35.00；额外活动预算消耗 5.00 | 通过 |
| 既有回归（营销上下文全部用例） | 无回归 | 45 个用例全绿（`RewardServiceTest` 11、`MarketingSeedTest` 2） |

## 7. 风险与限制

| 编号 | 风险/限制 | 处理 |
|------|----------|------|
| R1 | 退款、拒付、撤销交易不会扣回已发返现 | 本期不实现（Q8 结论）；需在客服口径与后续迭代中补齐冲正 |
| R2 | 同一交易产生两笔返现，外部对账若按交易聚合会看到 5% + 0.5% | 对账/报表需按 `campaign_id` 维度核对；文档与客服话术说明「合计 5.5%」 |
| R3 | 两活动预算各自 50000.00，财务看到的是两份额度 | 财务监控按「白金合计」口径汇总；预算耗尽时活动单独跳过 `BUDGET_EXHAUSTED` |
| R4 | 客户累计上限各自 300.00，白金客户合计上限实际为 600.00 | 若业务要求合计 300.00，需下调额外活动上限并重跑用例 |
| R5 | V3 种子活动直接 ACTIVE，生产部署即生效 | 生产发布前由运营复核生效时间；如需先建后发，可改为 DRAFT 并通过活动接口发布 |
| R6 | PRD 其余待确认项（Q3 返现基数与剔除项、Q7 白金判定与附属卡、Q9 参数维护人、Q10 目标指标）仍未确认 | 不阻塞本次配置化实现；上线前需业务确认，尤其 Q3 影响「消费基数」口径 |

## 8. 追溯矩阵

| 需求 | 设计章节 | 测试/验证 |
|------|---------|-----------|
| REQ-CB-01 活动参数配置 | §3 | `MarketingSeedTest.seedsPlatinumExtraCashbackCampaign` |
| REQ-CB-02 与既有活动叠加控制 | §2、§4 | `stacksExtraCashbackOnTopOfBaseCampaign` |
| REQ-CB-03 资格判定（产品/窗口/报名/门槛） | §4 | `extraCashbackKeepsMinimumAmountAndOwnCaps` |
| REQ-CB-04 计算与上限、预算 | §3、§4 | `stacks...`、`extraCashbackKeeps...` |
| REQ-CB-05 入账、幂等与重试 | §4 | `stacks...`（幂等）、既有 `keepsPendingWhenCreditFailsAndRetryIsIdempotent` |
| REQ-CB-06 客户可见性与运营效果 | §1（复用既有接口） | 既有 `reportsPerformanceAndDashboardSummary` |
| REQ-CB-07 记录与审计留痕 | §4（`reward_grant` 按活动留痕） | `stacks...`（两笔 grant 独立可查） |
