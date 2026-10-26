# 10 钱路径切片 A1 详细设计

> 上游：`09-scale-up-blueprint.md` 方案 A（账户 → 交易 → 支付 → 总账 → 对账）
> 状态：A1.1 实施中，A1.2–A1.4 设计待评审

## 1. 目标与非目标

**目标**：把"账户余额加减"升级为可支撑支付/授权的资金模型：

1. 账户由产品驱动（额度、费率、账单周期可配置）。
2. 资金冻结（hold）与可用额度可查询、可释放、可幂等重试。
3. 交易过账生成复式分录，账户与总账可对账。
4. 一条跨行转账可完成"受理→筛查→过账→总账→对账→回单"。

**非目标（本切片不做）**：真实清算网络接入、多币种核算、利息/账单完整引擎、监管报表。

## 2. 增量拆分

| 增量 | 内容 | 依赖 | 状态 |
|---|---|---|---|
| A1.1 | 账户产品化、资金冻结、可用额度 | 无 | 已实现 |
| A1.2 | 交易类型目录、过账规则、复式记账、冲正 | A1.1 | 已实现 |
| A1.3 | ledger-service：科目表、日记账、试算平衡、期间关账 | A1.2 | 已实现 |
| A1.4 | 对账、EOD 批处理、差异处理 | A1.3 | 已实现 |

## 3. A1.1 领域模型

### 3.1 聚合

**AccountProduct（配置聚合）**

| 字段 | 说明 |
|---|---|
| productCode | 产品编码，主键 |
| name / category / currency | 名称、类别（CREDIT/DEBIT/SAVINGS）、币种 |
| defaultCreditLimit / maxCreditLimit | 默认额度、额度上限 |
| defaultCashLimit / maxCashLimit | 默认取现额度、上限 |
| apr / monthlyFee / statementCycleDay | 年利率、月费、账单周期日 |
| active | 是否可售 |

**Account（账户聚合，扩展）**

- 新增 `productCode`，开户时必须引用有效产品。
- 可用额度 = `creditLimit - currentBalance - Σ(ACTIVE holds)`。
- 账户状态非 `Y` 时禁止冻结。

**AccountHold（冻结聚合）**

| 字段 | 说明 |
|---|---|
| holdId | UUID |
| accountId / amount | 账户、冻结金额 |
| status | ACTIVE / RELEASED / EXPIRED / CAPTURED |
| referenceType / referenceId | 来源类型与业务幂等键 |
| createdAt / expiresAt / releasedAt | 时间 |

### 3.2 业务规则

| 编号 | 规则 |
|---|---|
| AP-01 | 账户引用的产品必须存在且 `active=true`。 |
| AP-02 | 账户 `creditLimit` 不得超过产品 `maxCreditLimit`。 |
| AP-03 | 账户 `cashCreditLimit` 不得超过产品 `maxCashLimit`。 |
| AH-01 | 只有 `activeStatus=Y` 的账户可以冻结。 |
| AH-02 | 冻结金额必须大于 0，最多 2 位小数。 |
| AH-03 | 冻结金额不得超过当前可用额度。 |
| AH-04 | 同一账户 + `referenceId` 幂等：已存在 ACTIVE 记录且金额一致时返回原记录。 |
| AH-05 | 同一 `referenceId` 金额不一致，或记录已释放/过期/捕获时，拒绝复用。 |
| AH-06 | 释放已释放的 hold 幂等返回；非 ACTIVE 状态不可释放。 |
| AH-07 | 已过期（`expiresAt < now`）的 hold 不再占用可用额度。 |
| AH-08 | 创建/释放 hold 时对账户加悲观锁，避免并发超冻。 |

### 3.3 数据库（account-service，V4）

- `account_product`：产品目录，种子 CLASSIC / GOLD / PLATINUM。
- `account.product_code`：按现有额度映射产品。
- `account_hold`：冻结记录，`(account_id, reference_id)` 唯一索引。

### 3.4 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/account-products` | 产品列表 |
| GET | `/api/v1/account-products/{productCode}` | 产品详情 |
| GET | `/api/v1/accounts/{accountId}/credit-position` | 额度、余额、冻结、可用额度 |
| GET | `/api/v1/accounts/{accountId}/holds?status=ACTIVE` | 冻结列表 |
| POST | `/api/v1/accounts/{accountId}/holds` | 创建冻结 |
| DELETE | `/api/v1/accounts/{accountId}/holds/{holdId}` | 释放冻结 |

账户响应新增 `productCode`。

### 3.5 错误码

| 错误码 | HTTP | 场景 |
|---|---:|---|
| PRODUCT_NOT_FOUND | 404 | 产品不存在 |
| PRODUCT_INACTIVE | 409 | 产品停售 |
| CREDIT_LIMIT_EXCEEDS_PRODUCT_MAX | 409 | 额度超产品上限 |
| CASH_LIMIT_EXCEEDS_PRODUCT_MAX | 409 | 取现额度超上限 |
| ACCOUNT_INACTIVE | 409 | 账户状态非 Y |
| INVALID_HOLD_AMOUNT | 409 | 冻结金额非法 |
| INSUFFICIENT_AVAILABLE_CREDIT | 409 | 可用额度不足 |
| HOLD_NOT_FOUND | 404 | 冻结记录不存在 |
| HOLD_NOT_RELEASABLE | 409 | 状态不可释放 |
| HOLD_REFERENCE_CONFLICT | 409 | 幂等键金额冲突 |
| HOLD_REFERENCE_REUSED | 409 | 已关闭记录被复用 |

### 3.6 验收标准

1. `GET /account-products` 返回 3 个种子产品；账户 1 为 CLASSIC，账户 6 为 PLATINUM。
2. 账户 6 可用额度 = `8000 - 2450.75 - 冻结合计`。
3. 冻结 100 元后可用额度减少 100；释放后恢复；重复释放不报错。
4. 冻结超过可用额度返回 `INSUFFICIENT_AVAILABLE_CREDIT`。
5. 同 `referenceId` 重复提交金额一致返回同一 hold；金额不一致返回 `HOLD_REFERENCE_CONFLICT`。
6. 更新账户额度超过产品上限返回 `CREDIT_LIMIT_EXCEEDS_PRODUCT_MAX`。
7. 并发冻结不会突破可用额度（悲观锁 + 集成测试）。

## 4. A1.2 过账引擎（设计要点）

- `transaction_type` 目录：DEPOSIT、WITHDRAWAL、TRANSFER_IN/OUT、FEE、INTEREST、REVERSAL、ADJUSTMENT 等，每个类型绑定一组 `posting_rule`。
- 过账请求携带 `idempotencyKey`；`posting_batch` + `posting_leg` 两张表。
- 每个 leg 有方向（DR/CR）、科目、金额；同一批次借贷必须平衡。
- 冲正生成反向批次并关联原批次；原批次标记 `REVERSED`。
- 账户余额更新与分录写入同一本地事务；总账通过事件异步同步。

## 5. A1.3 总账服务（设计要点）

- 科目表（COA）：资产/负债/收入/费用，父子层级。
- 日记账：`journal_entry` + `journal_line`，借贷平衡校验。
- 过账规则：交易类型 → 科目映射，版本化。
- 试算平衡、期间关账、总账与子账对账。
- 事件消费：`PostingPosted` → 生成日记账；幂等键为 postingId。

## 6. A1.4 对账与 EOD（设计要点）

- 对账任务：账户余额 vs 分录汇总、总账 vs 子账、清算文件 vs 内部记录。
- 差异分类：缺失、金额不符、重复、时序差异；支持调账与挂账。
- EOD 作业图：计息 → 费用 → 账单 → 逾期滚动 → 总账结转 → 对账 → 报表。
- 断点、重跑幂等、失败告警。

## 7. 测试策略

- 领域单测：产品限额、可用额度、hold 状态机、并发边界。
- 控制器测试：Happy path、幂等、额度不足、产品停售、非法状态。
- 集成测试（后续）：Testcontainers + PostgreSQL，验证迁移与锁。
- 契约测试（后续）：OpenAPI 快照，防止字段破坏。
