# 18 额外返现活动的退款与冲正回退受理

> 状态：设计完成，进入实施
> 需求来源：`docs/requirements/DEMO-MKT-001-premium-cashback.md`（v0.1）
> 基线：`docs/design/17-premium-cashback-campaign.md`、ADR-013

## 1. 背景与输入

DEMO-MKT-001 的业务诉求是“白金卡客户未来 3 个月消费额外获得 0.5% 返现”。PRD 1.4 节把事项分为已确认与待确认两层：

- 已确认：客户群体为白金卡（`PLATINUM`）、期限 3 个月、额外比例 0.5%（`cashbackRate=0.005`）、对象为消费交易。上述四项与现有活动模型（期限、产品圈选、费率、期间判定、评估去重、失败重试）完全对应，**无需新增计算逻辑**。
- 待确认（12 项，责任方已登记）：活动起止日期、客户判定时点、消费范围、限额与预算、舍入口径、叠加关系、是否报名、退款追扣与补发口径、交易时点与时区、目标值、权限矩阵、ADR-009 资产缺失。

因此本次代码改动的唯一缺口是 PRD REQ-CB-07：退款与冲正发生时，系统缺少“已发放返现如何回退”的受理与可追溯记录。其余需求通过既有能力 + 运营配置落地，本次不写死任何待确认参数。

## 2. 范围

**本次实施**

- 活动新增可选配置 `reversalPolicy`（`FULL` / `PROPORTIONAL` / `NONE`），由业务在创建活动时显式给出；未给出即为“口径未确认”。
- 新增 `POST /api/v1/reward-reversals`：受理退款（`REFUND`）与冲正（`CHARGEBACK`）事件，按原消费交易号找到奖励记录，按活动口径计算追扣金额并落库。
- 追扣记录表 `reward_reversal`，含状态、口径原因、金额与幂等键。
- 未确认口径时**不移动资金**：记录 `PENDING` 并返回 409，由业务确认口径后再次提交或人工处理。

**本次不实施（含理由）**

| 项 | 理由 |
|----|------|
| 实际扣减客户账户 | account-service 只有贷记接口（`POST /accounts/{id}/credits`），无借记能力；新增借记涉及资金路径与账务口径，须待 12.3-8 确认后单独设计 |
| 部分退款在 `FULL` 口径下的处理 | 属于业务口径（12.3-8），实现返回 409 而不自行选定 |
| 活动前消费补发 | 业务口径（12.3-8），未实现 |
| 叠加上限与互斥 | 业务口径（12.3-6），保持既有“多活动各自发奖”行为，不做代码级合并 |

## 3. 候选方案

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| A：活动级口径配置 + 追扣记录 + 借记执行 | 在营销服务内配置口径，追扣直接调用账户借记 | 闭环最早 | 需新增 account 借记接口，资金路径与账务口径未确认；爆炸半径最大 |
| B：活动级口径配置 + 追扣记录 + 执行待定（采用） | 营销服务受理并计算追扣金额，口径未确认时只记录不执行 | 边界清晰；不臆造资金动作；口径确认后可只补执行器 | 追扣处于 `PENDING`，需人工或后续批次执行 |
| C：只在交易侧丢弃退款事件 | 不改营销服务，退款一律不追扣 | 零改动 | 等于默认“不追扣”，属于替业务选定口径；无审计记录 |

采用 B。选择理由是 PRD REQ-CB-07 明确要求“本期必须先提供可执行的回退能力与去重保护”，同时要求“未确认前实现不得自行选定追扣或保留策略”。B 用配置表达口径，用状态表达未决，两者都不替业务做决定。

### 3.1 量化权衡

| 方案 | 实现成本 | 资金风险 | 审计完整性 | 口径确认后的增量 |
|------|---------|---------|-----------|----------------|
| A | 高（跨两服务、迁移、并发） | 高（无口径即动账） | 完整 | 0 |
| B | 中（单服务、1 表 1 接口） | 低（不动账） | 完整（记录 + 原因） | 补借记执行器 |
| C | 低 | 中（应追不追） | 缺失 | 重做受理 |

## 4. 领域模型

```
transaction-service --(退款/冲正事件)--> marketing-service
                                           |-- RewardGrantRepository.findByTransactionId
                                           |-- Campaign.reversalPolicy
                                           |-- ReversalCalculator.decide
                                           '-- RewardReversalRepository.save
```

### 4.1 追扣口径（ReversalPolicy）

| 值 | 语义 | 可实现性 |
|----|------|---------|
| `null` | 业务未确认（默认，含既有活动） | 只记录，返回 409 |
| `NONE` | 不追扣 | 记录 `NONE` |
| `FULL` | 全额追扣；仅对全额退款/冲正成立 | 全额时给出追扣额；部分退款返回 409 |
| `PROPORTIONAL` | 按退款金额占原消费金额比例追扣，HALF_UP 2 位 | 直接计算 |

### 4.2 判定顺序（ReversalCalculator）

1. 奖励状态不是 `GRANTED` → `NONE`，原因 `REWARD_NOT_GRANTED`（未实际发放，无可追扣资金）。
2. 活动口径为 `null` → `PENDING`，原因 `REVERSAL_POLICY_UNCONFIRMED`。
3. 口径 `NONE` → `NONE`，原因 `NO_CLAWBACK`。
4. 口径 `FULL` 且退款金额 ≥ 原消费金额 → `PENDING`，追扣额 = 返现额，原因 `CLAWBACK_EXECUTION_PENDING`。
5. 口径 `FULL` 且部分退款 → `PENDING`，追扣额为空，原因 `PARTIAL_REVERSAL_POLICY_UNCONFIRMED`。
6. 口径 `PROPORTIONAL` → `PENDING`，追扣额 = 返现额 × 退款额 ÷ 原消费金额（HALF_UP 2 位），原因 `CLAWBACK_EXECUTION_PENDING`。

原因 2 与 5 视为口径未确认，接口返回 409；其余返回 200/201 并携带记录。

### 4.3 状态

| 状态 | 含义 |
|------|------|
| `PENDING` | 已受理，追扣金额已计算或待确认，资金尚未扣减 |
| `NONE` | 无需追扣（口径不追扣或奖励未发放） |

`DONE` / `FAILED` 在借记执行器落地时加入；本次不预置不可达状态。

### 4.4 表 `reward_reversal`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| reversal_id | VARCHAR(36) | 主键 | 服务端生成 |
| campaign_id | VARCHAR(36) | 非空，外键 campaign | 奖励所属活动 |
| reward_id | VARCHAR(36) | 非空，外键 reward_grant | 被追扣的奖励 |
| reversal_transaction_id | VARCHAR(64) | 非空 | 退款或冲正交易号 |
| reversal_type | VARCHAR(16) | 非空 | `REFUND` / `CHARGEBACK` |
| reversal_amount | NUMERIC(14,2) | 非空，> 0 | 退款或冲正金额 |
| clawback_amount | NUMERIC(14,2) | 可空，≥ 0 | 追扣金额；未确认时为空 |
| status | VARCHAR(16) | 非空 | `PENDING` / `NONE` |
| reason | VARCHAR(64) | 非空 | 口径原因码 |
| created_at | TIMESTAMP | 非空 | 受理时间 |
| version | BIGINT | 非空，默认 0 | 乐观锁 |

唯一索引 `(reversal_transaction_id, reward_id)`：同一退款交易对同一奖励只受理一次。

## 5. 接口契约

### 5.1 受理退款/冲正

`POST /api/v1/reward-reversals`

请求：

```json
{"reversalTransactionId":"0000000000000300","originalTransactionId":"0000000000000200",
 "reversalType":"REFUND","reversalAmount":1000.00}
```

字段：

| 字段 | 类型 | 必填 | 校验 | 错误码 |
|------|------|------|------|--------|
| reversalTransactionId | string(64) | 是 | 非空 | `INVALID_REVERSAL_TRANSACTION_ID` |
| originalTransactionId | string(64) | 是 | 非空 | `INVALID_TRANSACTION_ID` |
| reversalType | string(16) | 是 | `REFUND` / `CHARGEBACK` | `INVALID_REVERSAL_TYPE` |
| reversalAmount | decimal(14,2) | 是 | > 0 | `INVALID_AMOUNT` |

响应 201：

```json
{"reversals":[
  {"reversalId":"...","rewardId":"...","campaignCode":"PLATINUM_3M_BONUS_CASHBACK",
   "reversalType":"REFUND","reversalAmount":1000.00,"clawbackAmount":5.00,
   "status":"PENDING","reason":"CLAWBACK_EXECUTION_PENDING","createdAt":"2026-10-29T16:40:00"}]}
```

- 原消费交易无奖励记录：返回 201 与空数组 `{"reversals":[]}`，并记录 INFO 日志（PRD REQ-CB-07 异常矩阵第 1 行）。
- 同一 `(reversalTransactionId, rewardId)` 重复提交：返回既有记录，不重复计算（PRD US-017）。

错误码：

| 错误码 | HTTP | 触发条件 |
|--------|------|---------|
| `REVERSAL_POLICY_UNCONFIRMED` | 409 | 活动未配置追扣口径 |
| `PARTIAL_REVERSAL_POLICY_UNCONFIRMED` | 409 | `FULL` 口径下发生部分退款 |
| `INVALID_REVERSAL_TRANSACTION_ID` | 400 | 退款交易号缺失 |
| `INVALID_TRANSACTION_ID` | 400 | 原交易号缺失 |
| `INVALID_REVERSAL_TYPE` | 400 | 退款类型不在枚举内 |
| `INVALID_AMOUNT` | 400 | 退款金额 ≤ 0 |

409 场景下已受理的记录仍然保留，便于口径确认后继续处理；服务不做整体事务回滚（与 `RewardService` 的“资金事实优先”一致）。

### 5.2 创建活动新增字段

`POST /api/v1/campaigns` 增加可选字段 `reversalPolicy`，同时出现在 `GET /api/v1/campaigns/{id}` 响应中。缺省为 `null`，既有调用方无需修改。

## 6. 事务、并发与幂等

- 受理方法不加类级 `@Transactional`：记录保存后若判定为未确认口径，仍保留记录并抛出 409。
- 幂等键：`(reversal_transaction_id, reward_id)`；先查后写，唯一索引兜底并发，命中时返回既有记录。
- 乐观锁：`reward_reversal.version`；同一奖励的并发受理冲突由 `CONCURRENT_UPDATE`（409）返回。
- 本次不动账，因此不涉及跨服务补偿与冲正路径；借记执行器落地时需按资金操作补充补偿设计。

## 7. 需求覆盖矩阵

| PRD 条目 | 落地方式 | 设计/代码位置 |
|----------|---------|--------------|
| REQ-CB-01 活动创建与生命周期 | 既有能力 + 新增可选 `reversalPolicy` | `Campaign`、`CampaignController` |
| REQ-CB-02 客户资格与活动可见 | 既有能力 | `EligibilityController`、`Campaign.isProductEligible`、`isWithinWindow` |
| REQ-CB-03 客户报名 | 既有能力 | `EnrollmentService` |
| REQ-CB-04 消费返现评估与计算 | 既有能力（0.005 由活动配置传入） | `RewardService`、`RewardCalculator` |
| REQ-CB-05 返现入账与失败重试 | 既有能力 | `RewardService.credit`、`AccountClient` |
| REQ-CB-06 客户返现记录查询 | 既有能力 | `RewardController` |
| REQ-CB-07 退款与冲正回退 | 本次实施 | §4、§5.1、`RewardReversalService` |
| REQ-CB-08 运营效果与审计查看 | 既有能力 + 追扣记录可查询 | `CampaignService.performance`、`reward_reversal` |

## 8. NFR 应答

| 维度 | 指标 | 落地 |
|------|------|------|
| 性能 | 受理接口 p95 < 300ms（单表写入 + 一次索引查询） | 索引 `(reversal_transaction_id, reward_id)`、`reward_grant(transaction_id)` |
| 可用性 | 营销服务不可用不影响交易入账（既有 best-effort 调用） | 调用方在 transaction-service 侧只记 WARN |
| 一致性 | 受理记录与奖励状态最终一致；追扣执行前不改变账户余额 | 状态 `PENDING` 表明未执行 |
| 去重 | 同一退款交易对同一奖励只产生 1 条记录 | 唯一索引 + 先查 |
| 审计 | 每次受理记录类型、金额、口径原因与时间 | `reward_reversal` 全字段留存 |
| 安全 | 日志不打印卡号与客户号，只打印交易号与奖励号 | `RewardReversalService` 日志 |
| 可维护性 | 口径为枚举 + 单一计算器，确认后只替换执行器 | `ReversalPolicy`、`ReversalCalculator` |

## 9. 测试计划

| 层级 | 覆盖 |
|------|------|
| 领域 | `ReversalCalculatorTest`：未确认口径、`NONE`、`FULL` 全额与部分、`PROPORTIONAL` 舍入、未发放奖励 |
| 应用 | `RewardReversalServiceTest`：无奖励交易返回空列表、幂等重复提交、未确认口径 409 且记录保留、`NONE` 记录、多活动叠加时逐条受理、非法金额与类型 |
| 契约 | `RewardReversalControllerTest`：201 正常、409 未确认口径、400 参数错误 |
| 既有规则复核 | `PremiumBonusCashbackTest`：0.005 费率 1000.00 → 5.00；3 个月窗口边界日命中、次日不命中；重复交易只发一次 |

执行方式：`mvn -pl services/marketing-service test`（H2 + Flyway 迁移）。

## 10. 未实施与已知限制

1. 追扣资金执行需账号借记能力，待 12.3-8 确认后补充；当前记录停在 `PENDING`。
2. 部分退款在 `FULL` 口径下返回 409，未自行定义处理方式。
3. 活动前消费补发未实现（12.3-8）。
4. 叠加关系未做代码级约束（12.3-6）；同时满足的多个活动各自发奖，由运营配置决定是否并存。
5. 演示环境接口级鉴权仍不强制启用，生产化前由网关统一实施。
