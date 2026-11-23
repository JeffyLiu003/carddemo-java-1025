# 模拟需求包索引（2026-10）

> 用途：用于需求评审、排期与前后台改造演示。
> 说明：以下为**模拟需求**，业务指标、费率、时效均为演示假设，不构成生产合规结论。
> 基线：`dev-b`（已移除营销能力）；前期高端卡返现设计位于 `demo-b2` 分支的 `docs/design/17-premium-cashback-campaign.md`。

## 1. 需求清单

| 编号 | 名称 | 优先级 | 档位 | 主要影响服务 | 界面变化 | 建议版本 |
|---|---|---|---|---|---|---|
| REQ-2026-001 | 信用卡账单/消费分期 | P0 | L3 | installment(新)、transaction、billing、account、ledger、notification | 交易详情/账单详情新增分期入口、分期计划页、后台计划查询 | R1 |
| REQ-2026-002 | 信用卡挂失与补卡 | P0 | L2 | card、transaction、account、notification、audit | 卡片详情新增挂失/补卡向导、进度时间线、后台制卡队列 | R1 |
| REQ-2026-003 | 信用卡额度调整申请与审批 | P1 | L3 | limit、workflow、account、customer、notification、audit | 账户页提额入口、申请进度、后台审批工作台 | R2 |
| REQ-2026-004 | 交易争议在线申请与处理 | P1 | L3 | dispute、document、transaction、account、notification | 交易详情争议入口、证据上传、案件时间线、后台案件工作台 | R2 |

## 2. 排期建议

- **R1（4–6 周）**：REQ-2026-001、REQ-2026-002。两者都直接改变客户高频页面，且后台边界清晰。
- **R2（4–6 周）**：REQ-2026-003、REQ-2026-004。涉及审批/案件工作台与跨服务状态机，建议在 R1 稳定后做。

## 3. 共同假设

1. 演示环境沿用现有 Spring Boot 3 / PostgreSQL / 原生 SPA 技术栈。
2. 登录与角色沿用 IAM：普通用户 `U`、管理员 `A`；POC 不做接口级强鉴权。
3. 金额统一 `BigDecimal`/`numeric(12,2)`；ID 采用现有零填充或 UUID 规范。
4. 所有写接口必须支持去重键（接口字段 `idempotencyKey`）；关键操作写审计事件。
5. 涉及账户余额的变更必须通过 account-service；涉及会计影响必须过账到 ledger-service。
6. 通知采用 notification-service 模板（邮件/短信/站内），失败不回滚主流程。

## 4. 共同非功能要求

| 项 | 要求 |
|---|---|
| 性能 | 查询 p95 < 800ms；提交 p95 < 1200ms（本地 Compose） |
| 可用性 | 依赖服务不可用时，主流程给出明确错误码，不产生半完成资金状态 |
| 安全 | 卡号/证件号掩码；证据文件类型/大小白名单；越权访问返回 404/403 |
| 审计 | 申请、审批、状态迁移、金额变更全部记录 actor/action/resource/payload |
| 可测试性 | 每条需求至少覆盖主流程、边界、重复提交、下游失败四类用例 |

## 5. 评审产出

四份需求说明书统一采用 BA 需求说明书模板章节（标题与模板一致）：

| 章节 | 内容 |
|---|---|
| 文档信息 / 修订记录 | 文档编号、版本、业务负责人、需求来源；版本变更轨迹 |
| 1. 背景与目标 | 业务背景、可量化目标、术语表 |
| 2. 用户角色与权限 | 角色、职责、权限点 |
| 3. 范围定义 | 3.1 In-Scope / Out-of-Scope；3.2 假设与依赖 |
| 4. 功能需求 | 4.1 功能清单总览；4.2 每条核心 REQ 的字段级需求表与异常矩阵；4.3 界面改造清单 |
| 5. 业务规则 | 编号、规则、来源、优先级、验证点 |
| 6. 非功能需求 | 6.1 性能/可用性/可观测性；6.2 安全需求 |
| 7. 接口与集成 | 接口草案、请求示例、错误码清单、服务间集成 |
| 8. 数据需求 | 8.1 数据字典；8.2 数据安全（脱敏/加密/留存/访问） |
| 9. 场景矩阵 | 主流程/边界/异常 × 角色/数据接口/预期 |
| 10. 验收标准（可执行化） | 故事 / Given / When / Then / 数据准备 |
| 11. 依赖与约束 | 11.1 依赖；11.2 影响分析；11.3 约束 |
| 12. 风险与假设 | 12.1 风险与应对；12.2 假设；12.3 待确认事项 |
| 13. 合规标注 | 模拟监管、制度、SoD、隐私、审计 |

> 附录：每份文档末尾附「附录 A 章节编号对照（检查脚本兼容）」，用于与技能脚本
> `spec_completeness.py` 的旧章号（10 风险与应对、11 合规标注）对齐。

## 6. 技能脚本检查结果

检查命令（在仓库根目录执行）：

```bash
python3 ~/.config/opencode/skills/ba-requirement-analysis/scripts/spec_completeness.py docs/requirements/REQ-2026-001-installment.md
python3 ~/.config/opencode/skills/ba-requirement-analysis/scripts/ambiguity_check.py docs/requirements/REQ-2026-001-installment.md
```

| 文档 | spec_completeness 得分 | spec_completeness 结论 | ambiguity_check 结论 | 高严重度模糊词 |
|---|---|---|---|---|
| REQ-2026-001-installment.md | 100/100 | PASS | PASS | 0 |
| REQ-2026-002-card-loss.md | 100/100 | PASS | PASS | 0 |
| REQ-2026-003-limit-increase.md | 100/100 | PASS | PASS | 0 |
| REQ-2026-004-dispute.md | 100/100 | PASS | PASS | 0 |

说明：脚本的模糊词表包含「等」「相关」「正常」这类高频片段词，文档正文统一改写为具体表述（如「去重键」对应 `idempotencyKey`、「按卡级别取值」），未使用 `[待确认]` 占位符，待确认项改以「（待确认：…）」正文标注并集中在各文档 12.3 节。
