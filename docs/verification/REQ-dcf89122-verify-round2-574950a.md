# 第二轮验证记录（2026-11-13 19:10）· REQ-dcf89122

| 项 | 内容 |
|----|------|
| 验证版本 | `574950a04b29f5831af0911d3b3db6cf7aec65d0`（`test:` 提交，基于 `1a66fff`） |
| 变更范围 | 3 个测试文件 + PRD US-CB-001 数值修正 + 设计文档测试映射；**无生产代码改动** |
| 输入 | PRD v1.0（US-CB-001 余额已改为 2450.75）、设计文档 18、代码 574950a |
| 环境 | JDK 21.0.12.1、Maven 3.9.16、H2 `test` profile；无 Docker |
| 总体结论 | 10 条验收标准有自动化断言、1 条（US-CB-007）为部分覆盖；静态与安全检查通过；仅剩跨服务端到端与配置解析两项限制 |

---

## 1. 功能测试报告

| # | 命令 | 结果 |
|---|------|------|
| M1 | `mvn -B test` @ `services/marketing-service` | **45 通过 / 0 失败 / 0 跳过**，exit 0，BUILD SUCCESS |
| M2 | `mvn -B test` @ `services/transaction-service` | **66 通过 / 0 失败 / 0 跳过**，exit 0，BUILD SUCCESS |
| M3 | `mvn -B test` @ `services/account-service` | **39 通过 / 0 失败 / 0 跳过**，exit 0，BUILD SUCCESS |

用例来源：企业测试资产插件（`test-cases` 输入）仍未登记，本轮用例仍为 **PRD 派生**。

验收标准覆盖（相对第一轮的变化）：

| 用例 | 状态 | 断言位置 |
|------|------|---------|
| US-CB-001 120.00 → 0.60 | **covered（服务层）** | `RewardServiceTest.grantsExtraHalfPercentWithinWindowAndCapsSingleTransaction`；账户侧 `AccountControllerTest.creditReducesBalanceAndIsIdempotentPerCreditId` |
| US-CB-002 窗口外跳过 | covered | 同上（2027-02-13 → `OUTSIDE_WINDOW`）+ `RewardServiceTest.skipsCampaignsThatDoNotApply` |
| US-CB-003 产品不符 | covered | `RewardServiceTest.skipsCampaignsThatDoNotApply` |
| US-CB-004 幂等 | covered | `RewardServiceTest.evaluationIsIdempotentPerCampaignAndTransaction` |
| US-CB-005 客户上限 | covered | `RewardServiceTest.capsCashbackAtCustomerLimitAndThenSkips` |
| US-CB-006 预算耗尽 | covered | `RewardServiceTest.skipsWhenCampaignBudgetIsExhausted` |
| US-CB-007 贷记失败重试 | **partial** | `RewardServiceTest.keepsPendingWhenCreditFailsAndRetryIsIdempotent`（模拟失败，非真停服务；无「仅贷记一次」计数断言） |
| US-CB-008 金额 0 → 400 | **covered（接口层）** | `MarketingControllerTest.evaluationRejectsZeroAmount`（HTTP 400 + `code=INVALID_AMOUNT`） |
| US-CB-009 多活动叠加 | **covered** | `RewardServiceTest.stacksPremiumAndExtraHalfPercentCampaigns`（2 条 grant：30.00 + 5.00，预算独立，`credit` 2 次） |
| US-CB-010 门槛 50.00 | covered | `RewardServiceTest.skipsTransactionBelowMinimumAmount` |
| US-CB-011 非消费类不评估 | covered | `PostingServiceTest.nonPurchasePostingSkipsMarketingEvaluation` |

未执行：跨服务端到端（无 Docker）、真实容器故障演练（US-CB-007 的停/恢复）、运行环境中的活动配置创建。首轮的根工程 29 模块回归结果（28 通过、`authorization-service` 1 例既有失败）在 `REQ-dcf89122-functional-test-report.md` 中保留。

## 2. 静态检查报告

`mvn -B -DskipTests compile com.github.spotbugs:spotbugs-maven-plugin:4.8.3.0:spotbugs`（未改构建文件）：

| 模块 | exit | Bug 总数 | 命中本次涉及类 |
|------|------|---------|---------------|
| marketing-service | 0 | 26（`EI_EXPOSE_REP2` 14、`EI_EXPOSE_REP` 12） | 0（`RewardService`/`MarketingController` 无告警） |
| transaction-service | 0 | 49（`EI_EXPOSE_REP2` 27、`EI_EXPOSE_REP` 21、`CT_CONSTRUCTOR_THROW` 1） | 0（`PostingService` 无告警） |

与首轮一致，无新增告警。局限同前：未做依赖漏洞扫描、未扫全仓。

## 3. Agent Review 报告

审查 `1a66fff..574950a` 差异（3 个测试文件 + 2 处文档）：

| 检查项 | 结论 |
|--------|------|
| 新增用例是否真正断言需求 | 是：叠加场景断言两条 grant 的金额与预算隔离；费率场景断言 0.60 与 6000.00→30.00 封顶；窗口场景断言末日外跳过；接口层断言 HTTP 400 与错误码 |
| 用例是否可重复、无时间依赖 | 是：固定 `LocalDateTime`，`@Transactional` 回滚，数据清理与既有用例一致 |
| 是否引入生产代码风险 | 否：仅测试与文档 |
| 首轮 RV-01/RV-02/RV-03/RV-05 | **已关闭**（多活动断言、0.005 断言、金额 0 接口用例、PRD 数值勘误） |
| RV-04（配置解析/大小写/跳过日志） | 仍开放，属低优先级改进 |

遗留建议（不阻断）：为 `marketing.reward-type-codes` 增加参数化用例（`01,02` / 空串 / 含空格），并统一 `trim().toUpperCase()`。

## 4. 安全报告

- 本轮改动为测试代码与文档，**不改变生产运行面**：无新接口、无新输入解析、无新 SQL、无密钥或口令。
- 首轮登记的三项既有风险不因本轮变化：SEC-01 营销接口无鉴权（ADR-007）、SEC-02 `application.yml` 默认 DB 口令、SEC-03 无依赖漏洞扫描（当前网络不可行）。
- 结论：通过与首轮相同的判断——本次变更安全影响中性，无阻断项。

## 5. 问题与复测结果

| ID | 首轮结论 | 复测结果 |
|----|---------|---------|
| ISS-01 多活动叠加无断言 | 阻断 | **已关闭**：`stacksPremiumAndExtraHalfPercentCampaigns` 实测 2 条 grant、预算独立、2 次贷记，M1 通过 |
| ISS-02 0.005 费率无金额断言 / PRD 数值不符 | 阻断 | **已关闭**：0.60 与 30.00 封顶断言通过；PRD 余额基数改为 2450.75/2450.15 与种子一致 |
| ISS-03 金额 0 无用例 | 中 | **已关闭**：接口层断言 400 `INVALID_AMOUNT` |
| ISS-04 无容器，端到端未执行 | 低 | **未关闭**（环境限制）：仍无 Docker；建议在具备容器的环境补端到端冒烟并给 `smoke-test.sh` 加断言 |
| ISS-05 authorization-service 既有失败 | 低（范围外） | 未复测，与本需求无关，仍需单独派单 |
| ISS-06 配置解析无测试、大小写未归一、跳过无日志 | 低 | **未关闭**，不阻断交付 |

## 6. 登记状态说明

本轮结果尚未进入 Teams 台账：verify 节点已于 revision 92 提交，CLI 写入对当前会话封闭。需 Owner 在页面退回任务后，方可按 `574950a` 重新提交 develop 结果并重跑 verify。

代码提交已于 2026-11-13 19:51 +08:00 成功推送到 `origin/codex/carddemo-req-dcf89122-605b-4263-92f6-9a893c967a7e-f53dcd95`（远端 head = `574950a`）。本报告随 `docs:` 提交一并纳入该分支。
