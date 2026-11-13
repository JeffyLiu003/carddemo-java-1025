# 发现问题与复测结果 · REQ-dcf89122

| 项 | 内容 |
|----|------|
| 检查版本 | `1a66fff39d5f6dd06b78ff868ae7347468873047` |
| 检查时间 | 2026-11-13 18:30～18:55 +08:00 |
| 复测状态 | **ISS-01、ISS-02 未修复，本轮无法复测**（修复需在 develop 补测试并产生新代码版本） |

## 问题清单

| ID | 严重度 | 问题 | 证据 | 处理建议 | 复测结论 |
|----|--------|------|------|---------|---------|
| ISS-01 | **阻断** | 无「两个 ACTIVE 活动同时命中同一交易」的自动化断言，D-01「额外 0.5% 与既有 5% 独立叠加」在服务层无证据（`RewardService` 遍历 `ACTIVE` 活动仅靠代码阅读推断） | 功能报告 §2 US-CB-009；所有评估断言均为单条 grant（`RewardServiceTest.java:105/171/221`、`MarketingControllerTest.java:293`） | develop 补集成用例：两个活动均 ACTIVE 且通过窗口/产品/门槛 → `grants` 两条，各自上限与预算独立 | 未修复，未复测 |
| ISS-02 | **阻断** | 需求本体（0.005 费率）与验收数值未被任何测试断言；PRD 的 120.00→0.60、余额 2550.75→2550.15 无对应证据；且 PRD 数值与种子数据不符（种子白金账户 2450.75，账户号 `00000000006` 无 0.005 活动种子） | 功能报告 §2 US-CB-001；`V2__account_seed.sql:15`；`V2__marketing_seed.sql:7-10` | develop 补 0.005 费率断言（120.00→0.60、1000.00→封顶 30.00）；同时修正 PRD 附录数值或补活动种子（二者取一） | 未修复，未复测 |
| ISS-03 | 中 | `amount=0` 无 400 `INVALID_AMOUNT` 用例；`EvaluateRewardRequest.amount` 仅 `@NotNull`，0/负数依赖服务层拦截 | 功能报告 §2 US-CB-008；`RewardService.java:226-228`；`EvaluateRewardRequest.java:31` | 建议补接口层用例；是否加 `@Positive` 由 develop 与业务判断（会影响错误码一致性） | 未修复，未复测 |
| ISS-04 | 低 | 环境无 Docker，跨服务端到端（真实 HTTP：过账→评估→贷记→余额）未执行；`smoke-test.sh` 只 print 不 assert | 功能报告 §3 | 具备容器环境时执行端到端冒烟，并把 `smoke-test.sh` 改成带断言的验证 | 未修复，未复测 |
| ISS-05 | 低（非本次范围） | `authorization-service` 既有单测失败 `purgesExpiredDetailsAndReleasesCreditBalance:266`（期望释放 100.00） | 根工程 `mvn -B test` R1；`git diff 1ddd92c..HEAD -- services/authorization-service` 为空 | 与本次需求无关，另行派单；登记以免污染「全仓绿」判断 | 不属于本轮复测范围 |
| ISS-06 | 低 | 配置项 `marketing.reward-type-codes` 的解析（多值/空值/空格）无测试；大小写不敏感未处理；跳过非消费类时无日志 | Agent Review RV-04 | 补参数化用例；按需统一 upper-case 并加 debug 日志 | 未修复，未复测 |

## 本轮判定

- 静态检查、安全检视通过；功能测试与 Agent Review 未通过（ISS-01、ISS-02 为阻断项）。
- 阻断项的根因是**测试证据缺失**，非生产代码缺陷；但按「编译不能代替测试、缺证据不能算通过」的口径，本轮不能声明 verify.current（全部检查通过）。
- 解除条件：develop 补齐 ISS-01、ISS-02 所需用例（并顺带处理 ISS-03/ISS-06）→ 产生新提交 → 重新取 verify 输入并重新执行本轮全部检查。
