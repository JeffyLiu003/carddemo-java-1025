# 18 白金卡额外 0.5% 消费返现（REQ-f1537278）

| 项 | 内容 |
|---|---|
| 需求 | `REQ-f1537278-419c-4aac-a68d-0d73cf31d5c3` credit card cashback on spending 20261105 |
| PRD | `docs/requirements/REQ-f1537278-419c-4aac-a68d-0d73cf31d5c3-prd.md` v0.2 |
| 依据 | 需求原文（S1）、`teams/docs/cashback-contract.md`（S3）、现有实现 `docs/design/17-premium-cashback-campaign.md`（S7）、ADR-013 |
| 参数确认 | 开发者在 2026-11-06 18:13（+08:00）本会话中确认的参数：沿用现有白金活动口径（最低消费 50.00、单笔上限 30.00、客户累计上限 300.00、预算 50 000.00）、活动起始 2026-11-01、作为独立第二个活动叠加。此为**会话级确认**，不等同于 Teams 已登记的需求确认（`agreement.confirmations` 仍为空） |

## 1. 结论

现有营销能力（活动 / 报名 / 返现发放 / 账户贷记）已经覆盖"限期、指定卡等级、按比例返现"的全部机制，见 17 号设计与 ADR-013。本需求**不需要新增模块或表**，交付物为：

1. 一条活动种子数据（`V3__platinum_extra_cashback.sql`），费率 0.005、卡等级 `PLATINUM`、期限 3 个月、无需报名；
2. 一处输入校验补齐：评估金额必须为整分，非整分金额拒收（原实现静默四舍五入，与 S3 的拒收规则不一致）；
3. 对应单元测试。

## 2. 备选方案

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| A. 新增一条活动记录（配置式） | 复用既有 PC-01…PC-08 规则链；无代码分支；活动可独立暂停/结束/统计 | 受活动模型强制约束：最低消费、单笔/客户上限、预算必须给定 | **采用** |
| B. 在代码中为"额外 0.5%"写特殊分支 | 不受上限字段约束 | 硬编码业务规则，绕过活动生命周期与统计；与 17 号实现分叉 | 不采用 |
| C. 修改现有 `PLATINUM_3M_CASHBACK` 为 0.5% | 改动量最小 | 会改变已上线 5% 活动的语义与历史口径 | 不采用 |

## 3. 行为定义

| 项 | 取值 | 来源 |
|---|---|---|
| 活动编码 | `PLATINUM_EXTRA_05` | 本设计 |
| 费率 | 0.005（0.5%） | S1 |
| 卡等级 | `PLATINUM` | S1 |
| 期限 | 2026-11-01 起 3 个月，窗口 2026-11-01 … 2027-01-31（含端点） | S3 窗口 2026-11-01T00:00+08:00 … 2027-02-01T00:00+08:00 独占；PC-01 的 `end_date = start + 3 月 − 1 天` 与之等价 |
| 报名 | 不需要（`enrollment_required = FALSE`） | S3 |
| 最低消费 | 50.00 | 会话确认 |
| 单笔上限 | 30.00 | 会话确认 |
| 客户累计上限 | 300.00 | 会话确认 |
| 预算 | 50 000.00 | 会话确认 |
| 叠加 | 与其它 ACTIVE 活动并存，逐条评估；同一交易可分别命中 | S7 评估循环 |
| 金额口径 | 评估金额必须为正整数分；非整分直接拒收 `INVALID_AMOUNT` | S3 |
| 舍入 | 返现金额 HALF_UP 到分（如 120.00 × 0.5% = 0.60） | S3 / S7 |

窗口判定使用交易发生日期 `occurredAt.toLocalDate()`（服务本地时区），与既有实现一致；S3 以 +08:00 定义边界，部署时区需为 Asia/Shanghai，已在 PRD OI-07 记录。

## 4. 影响面

| 位置 | 变更 |
|---|---|
| `marketing-service/.../db/migration/V3__platinum_extra_cashback.sql` | 新增活动种子 |
| `marketing-service/.../application/RewardService#requireAmount` | 非整分金额拒收 |
| `marketing-service/.../application/PlatinumExtraCashbackSeedTest` | 新增：种子参数、窗口边界、卡等级、最低消费、0.5% 计算 |
| `marketing-service/.../application/RewardServiceTest` | 新增：非整分金额拒收且不产生发放记录 |

未改动：账户贷记契约、交易服务调用点、前端。5% 与金卡活动保持不变。

## 5. 需求覆盖

| 需求 | 覆盖位置 |
|---|---|
| REQ-001 额外 0.5% | §3 费率 + `PlatinumExtraCashbackSeedTest#grantsExtraHalfPercentWithoutEnrollment` |
| REQ-002 三个月窗口 | §3 期限 + `#appliesOnBothWindowBoundaries`、`#skipsSpendingOutsideTheWindow` |
| REQ-003 整数分与舍入 | §3 金额口径、舍入；`#grantsExtraHalfPercentWithoutEnrollment` |
| REQ-009 非法金额拒收 | §3 金额口径 + `RewardServiceTest#rejectsAmountThatIsNotAWholeNumberOfCents` |
| REQ-005 上限与预算 | §3 上限、预算（沿用既有 PC-02 判定，本设计仅设定值） |
| REQ-006 无报名 | `enrollment_required = FALSE` + `#grantsExtraHalfPercentWithoutEnrollment` |

REQ-004（贷记时序）、REQ-007（客户可见）、REQ-008（运营台）由既有实现承接，本需求不新增改动；其口径仍以 PRD 开放项为准。

## 6. 验证

| 项 | 结果 |
|---|---|
| 环境 | Microsoft OpenJDK 21.0.13、Maven 3.9.9（工作区外的便携工具链，未修改仓库） |
| 命令 | `mvn -B -ntp -pl services/marketing-service -am test` |
| 结果 | `BUILD SUCCESS`，Tests run: 48, Failures: 0, Errors: 0, Skipped: 0 |
| 新增用例 | `PlatinumExtraCashbackSeedTest` 5 个（种子参数、无报名发放 0.60、窗口两端、窗口外跳过、卡等级与最低消费跳过）；`RewardServiceTest#rejectsAmountThatIsNotAWholeNumberOfCents` 1 个 |
| Flyway | 迁移校验 3 个版本通过，schema 版本 3 |
| 范围说明 | 仅构建并测试 `marketing-service`；本次未改动其它服务，未运行全仓测试与端到端 smoke 脚本（需 Docker 与全部服务拉起） |

## 7. 风险与遗留

- 参数来自会话确认，尚未写入 Teams 需求确认；若业务口径不同，只需改种子中的四个数值。
- 非整分拒收会让原先"静默四舍五入"的调用方收到 400 `INVALID_AMOUNT`；本仓库内无调用方依赖旧行为（已检索）。
- 叠加产生合并返现：同一笔 PLATINUM 消费若同时命中 5% 活动（需报名），两条返现会分别发放。若业务要求互斥，需要在活动模型上增加排他字段（PRD OI-03 未决）。
