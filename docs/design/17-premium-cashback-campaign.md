# 17 高端信用卡分期返现营销能力

> 状态：设计完成，进入实施
> 参考：已移除的旧营销模块（08/ADR-009），本方案重新设计为“高端卡 + 期限活动 + 消费返现”的窄能力

## 1. 需求与验收标准

### 1.1 业务场景

面向白金卡等价值客户，推出“未来 N 个月消费返现”活动：

- 活动按卡产品等级圈选客户（如 PLATINUM、GOLD）；
- 活动有明确起止日期，持续 3 个月或任意 1–24 个月；
- 客户报名后，活动期内每笔达标消费按比例返现；
- 返现有单笔上限、客户累计上限、活动总预算；
- 返现实时入账到信用卡账户（冲减欠款），失败可重试；
- 运营可创建/发布/暂停/结束活动并查看效果。

### 1.2 验收标准

1. 创建活动时指定 `durationMonths`，系统计算 `endDate = startDate + N 月 - 1 天`。
2. 只有活动期内、产品等级匹配、已报名（若要求报名）、金额达标的消费才返现。
3. 返现 = `min(消费×返现率, 单笔上限, 客户剩余额度, 活动剩余预算)`，2 位小数。
4. 同一活动 + 同一交易只返现一次；重复入账不重复返现。
5. 返现通过 account-service 贷记接口实时冲减欠款；失败保留 PENDING 可重试。
6. 活动预算/客户上限不会被突破（单实例内）。
7. 前台可完成：客户查看活动→报名→查看返现；管理员创建→发布→暂停/结束→看效果。

## 2. 影响分析

| 维度 | 影响 |
|---|---|
| 代码 | 新增 marketing-service；account-service 增加通用贷记接口；transaction-service 恢复“入账后评估返现”客户端；web-ui 增加营销菜单/页面 |
| 数据 | 新增 `carddemo_marketing`（campaign/enrollment/reward_grant）；account 新增 `account_credit`（V6） |
| 接口 | 新增活动/报名/资格/评估/奖励/效果 API；account 新增 `POST /accounts/{id}/credits` |
| 配置 | transaction 增加 `MARKETING_SERVICE_URL/MARKETING_ENABLED`；web-ui 增加代理；Compose 增加 marketing 容器 |
| 文档 | 新增本设计、ADR-013；更新 README/服务清单/API 目录/运行手册/冒烟脚本 |
| 安全 | 与旧模块相同：POC 未强制鉴权；返现接口需幂等键；金额/费率做边界校验 |
| 兼容 | 账户贷记为新增接口，不改既有还款/入账语义；旧 `reward_credit` 已删除，使用新 `account_credit` |

## 3. 架构设计

```
transaction-service --(入账成功后, best-effort)--> marketing-service
                                                    |-- AccountClient: 产品等级 / 贷记返现
                                                    |-- 本地: campaign / enrollment / reward_grant
account-service <--(POST /accounts/{id}/credits, creditId 幂等)-- marketing-service
web-ui -------------------------------------------> marketing-service
```

- 返现评估失败不影响入账（资金事实优先），只记 WARN；奖励保留 PENDING 可重试。
- 活动预算/客户上限在营销库内校验；单实例演示，跨实例需数据库行锁/分布式锁（见风险）。
- 账户贷记使用 `creditId` 唯一键，重复调用不重复冲减。

### 3.1 表（marketing-service）

**campaign**

`campaign_id`、`code`（唯一）、`name`、`description`、`status`（DRAFT/ACTIVE/PAUSED/ENDED）、
`start_date`、`duration_months`、`end_date`、`eligible_product_codes`（逗号分隔）、
`min_transaction_amount`、`cashback_rate`、`max_cashback_per_transaction`、
`max_cashback_per_customer`、`budget`、`consumed_budget`、`enrollment_required`、
`created_at`、`updated_at`、`version`。

**enrollment**

`enrollment_id`、`campaign_id`、`account_id`、`card_number`、`customer_id`、`status`、
`enrolled_at`、`version`；唯一 `(campaign_id, account_id)`。

**reward_grant**

`reward_id`、`campaign_id`、`transaction_id`、`account_id`、`customer_id`、`card_number`、
`transaction_amount`、`cashback_amount`、`status`（PENDING/GRANTED/FAILED）、`failure_reason`、
`created_at`、`granted_at`、`version`；唯一 `(campaign_id, transaction_id)`。

### 3.2 账户贷记（account-service，V6）

`account_credit`：`credit_id`（PK）、`account_id`、`source_type`、`amount`、`description`、`created_at`。

`POST /api/v1/accounts/{accountId}/credits`：

```json
{"creditId":"reward-uuid","sourceType":"CASHBACK","amount":30.00,"description":"PLATINUM_3M cashback"}
```

- `creditId` 幂等；金额 > 0；账户存在；余额可负（溢缴）。
- 返回账户快照。

### 3.3 关键规则

| 编号 | 规则 |
|---|---|
| PC-01 | `durationMonths` 1–24；`endDate = startDate + N 月 - 1 天` |
| PC-02 | 返现率 0–1；单笔/客户上限 > 0；预算 > 0；产品等级非空 |
| PC-03 | 仅 ACTIVE 活动参与评估；交易日期在 `[startDate, endDate]` |
| PC-04 | 账户产品编码在 `eligibleProductCodes` 内 |
| PC-05 | `enrollmentRequired=true` 时必须已报名 |
| PC-06 | 消费金额 ≥ `minTransactionAmount` |
| PC-07 | 返现 = min(金额×费率, 单笔上限, 客户剩余, 预算剩余)，HALF_UP 2 位 |
| PC-08 | 同一活动+交易幂等；已评估直接跳过 |
| PC-09 | 客户累计上限按 PENDING+GRANTED 计算；预算在创建 PENDING 时预占 |
| PC-10 | 返现贷记失败保留 PENDING；重试幂等；成功后 GRANTED + 记账时间 |

## 4. API 契约

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/campaigns` | 创建活动（DRAFT，201） |
| GET | `/api/v1/campaigns?status=&page=&size=` | 活动列表 |
| GET | `/api/v1/campaigns/{id}` | 详情 |
| POST | `/api/v1/campaigns/{id}/activate` | 发布（DRAFT/PAUSED→ACTIVE） |
| POST | `/api/v1/campaigns/{id}/pause` | 暂停（ACTIVE→PAUSED） |
| POST | `/api/v1/campaigns/{id}/end` | 结束（ACTIVE/PAUSED→ENDED） |
| POST | `/api/v1/campaigns/{id}/enrollments` | 报名（201，幂等） |
| GET | `/api/v1/campaigns/{id}/enrollments?page=&size=` | 报名列表 |
| GET | `/api/v1/eligibilities?accountId=&cardNumber=&customerId=` | 客户可见活动与资格 |
| POST | `/api/v1/reward-evaluations` | 交易返现评估（内部） |
| GET | `/api/v1/rewards?accountId=&customerId=&page=&size=` | 返现记录 |
| POST | `/api/v1/rewards/{rewardId}/retry` | 待入账重试 |
| GET | `/api/v1/campaigns/{id}/performance` | 活动效果 |
| GET | `/api/v1/marketing/summary` | 看板汇总 |

创建活动示例：

```json
{"code":"PLATINUM_3M_CASHBACK","name":"白金卡 3 个月消费返现",
 "description":"活动期内消费 5% 返现，单笔最高 30，累计最高 300",
 "startDate":"2026-10-01","durationMonths":3,
 "eligibleProductCodes":["PLATINUM"],
 "minTransactionAmount":50.00,"cashbackRate":0.05,
 "maxCashbackPerTransaction":30.00,"maxCashbackPerCustomer":300.00,
 "budget":50000.00,"enrollmentRequired":true}
```

评估请求：

```json
{"transactionId":"0000000000000200","accountId":"00000000006",
 "cardNumber":"3344556677889900","customerId":"000000001",
 "amount":1000.00,"occurredAt":"2026-10-26T10:15:00"}
```

响应：

```json
{"grants":[{"rewardId":"...","campaignCode":"PLATINUM_3M_CASHBACK",
            "cashbackAmount":30.00,"status":"GRANTED"}],
 "skipped":[{"campaignCode":"GOLD_6M_CASHBACK","reason":"PRODUCT_NOT_ELIGIBLE"}]}
```

## 5. 前台

- 新增一级菜单“营销”，二级：
  - `#/premium-offers`（客户）：账户/卡号查询 → 活动列表/资格/报名；我的返现记录、待入账重试。
  - `#/premium-campaigns`（管理员）：创建活动（期限月数、产品等级、费率、上限、预算）、列表、发布/暂停/结束、效果。
- 首页增加入口；代理 `/api/v1/campaigns`、`/eligibilities`、`/reward-evaluations`、`/rewards`、`/marketing` → marketing-service。

## 6. Review 清单

- [ ] 领域不依赖 Spring/JPA 以外的外层；
- [ ] 金额/费率边界、活动期限、幂等键、预算/上限冲突处理；
- [ ] 营销失败不影响入账；重试幂等；
- [ ] 账户贷记 `creditId` 唯一，余额可负；
- [ ] API 错误码/HTTP 语义一致；分页结构一致；
- [ ] 无敏感信息日志；无 SQL 拼接；
- [ ] 前端 esc/money/date 处理；管理员权限。

## 7. 测试计划

- 单元：活动期限计算、资格（产品/窗口/报名/金额）、返现计算与上限、预算/客户上限、幂等。
- 集成：创建→发布→报名→评估→贷记→重试；重复交易不重复返现；预算耗尽。
- 控制器：201/409/404/400/503。
- 交易：入账后调用营销；营销失败不回滚。
- 账户：贷记幂等、余额可负、金额校验。
- UI：`node --check`；页面接口契约；全仓 `mvn test`。
- 端到端：白金账户报名 → 消费 1000 → 返现 30 → 余额减少 30 → 重复入账不重复返现。

## 8. 风险与回退

- 预算/上限并发：单实例演示用活动行锁；多实例需分布式锁或数据库条件更新。
- 返现实时性：同步调用，最坏 +2s；失败只告警。
- 活动日期使用服务器时区（本地演示）；生产需统一 UTC/业务时区。
- 回退：关闭 `MARKETING_ENABLED` 停止评估；删除 Compose 营销服务与菜单；账户贷记接口可保留为通用能力。

## 9. Review 结论

已按第 6 节清单完成自检：

- 领域层不依赖 Spring Web；金额/费率/期限/产品等级边界在聚合根与计算器内校验。
- 评估幂等键 `(campaignId, transactionId)`；返现贷记 `creditId` 幂等；重复入账只返回 duplicates。
- 预算在创建 PENDING 时预占，客户上限按 PENDING+GRANTED 汇总；贷记失败保留 PENDING 可重试。
- 营销异常只 WARN，不影响 PostingService 入账；账户贷记用行锁串行化余额变更。
- 控制器统一 `{code,message,details?}` 与分页 `{items,page,size,total}`；前台动态文本 esc。
- 已知限制：预算/上限为单实例保护；报名接受的 customerId 未与卡归属做二次校验（建议调用方传卡真实客户）；本期不含积分/A-B/复杂客群。

## 10. 测试结果

- 全仓 `mvn test`：**815 个用例全绿**（marketing 42、account 39、transaction 64）。
- 营销单测覆盖：3/6 个月期限、产品资格、报名幂等、返现计算与单笔/客户/预算上限、交易幂等、贷记失败 PENDING/重试、活动状态迁移。
- 端到端（Docker Compose）：
  - 白金账户 6 报名 `PLATINUM_3M_CASHBACK`；
  - 消费 1000.00 → 返现 30.00（5%，单笔上限 30）；
  - 账户余额 2455.75 → 3425.75（+1000 −30）；
  - 重复提交同一交易号 → `duplicates`，返现记录仍为 1 条；
  - 活动效果：报名 1、奖励 1、已发放 1、返现总额 30、预算使用率 0.06%、转化率 100%。
- 前端：`node --check` 通过；代理 `/eligibilities`、`/rewards`、`/campaigns` 正常；导航新增“营销”与两个二级入口。
