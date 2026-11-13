# 补充验证记录（2026-11-13 18:50）· REQ-dcf89122

> 本记录不修改已提交的 verify 报告；它补充「用临时用例实测」的新证据，供 Owner 决策时参考。
> 临时用例 `services/marketing-service/src/test/java/com/carddemo/marketing/application/ReqVerifyTempTest.java` **未提交**（untracked），代码版本仍为 `1a66fff39d5f6dd06b78ff868ae7347468873047`。

## 1. 为什么补这一步

上一轮 verify 判定 ISS-01（多活动叠加无断言）、ISS-02（0.005 费率无金额断言）、ISS-03（金额 0 无用例）为证据缺口。按开发者要求「先不提交、先验证」，用一次性用例直接实测这三项，而不是靠读代码推断。

## 2. 执行方式与结果

| 项 | 内容 |
|----|------|
| 命令 | `mvn -B -Dtest=ReqVerifyTempTest -DfailIfNoTests=false test`（`services/marketing-service`） |
| 环境 | JDK 21.0.12.1、Maven 3.9.16、H2（`test` profile）、`AccountClient` 打桩 |
| 结果 | `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，exit 0，耗时 12.19s |
| 时间 | 2026-11-13T10:50:03Z（18:50 +08:00） |
| 日志 | `D:\teams-talent-demo\teams\.runtime\work-contexts\3dcf4860-…-temp-verify.log`，SHA256 `5CD9A35670313BE4D3832775E699461696E1BEFB17E739ABCB27FE0EAE304DDB` |

## 3. 四个场景的实测结论

| 场景 | 用例 | 实测结果 | 对应标准 |
|------|------|---------|---------|
| 多活动叠加 | `stackedCampaignsGrantBothCashbacks` | 5% 活动（费率 0.05，需报名）与 0.5% 活动（费率 0.005，免报名）同为 `ACTIVE`，1000.00 交易产生 **2 条 grant**：30.00 与 5.00；`skipped` 为空；5% 活动 `consumedBudget=30.00`（各自独立）；`accountClient.credit` 被调用 **2 次** | US-CB-009 / D-01 |
| 0.005 费率金额 | `extraHalfPercentMatchesPrdAmounts` | 120.00 → **0.60**；1000.00 → **5.00**；活动 `consumedBudget=5.60` | US-CB-001 |
| 窗口末日 | `transactionAfterWindowIsSkipped` | 活动 2026-11-13 起 3 个月（至 2027-02-12），交易日期 2027-02-13 → `grants` 空、`reason=OUTSIDE_WINDOW`、未调用 `credit` | US-CB-002 |
| 金额 0 | `zeroAmountIsRejected` | 抛 `InvalidRequestException`，`code=INVALID_AMOUNT`，未调用 `credit` | US-CB-008 |

结论：**产品行为与 PRD 一致**，ISS-01/ISS-02/ISS-03 的「无证据」问题在服务层已被实测关闭（接口层 400 映射仍未验证）。

## 4. 需要更正的两处此前表述（诚实记录）

1. 我在 `REQ-dcf89122-issues-retest.md`（ISS-02）与 `REQ-dcf89122-agent-review-report.md`（RV-02）里写「建议补 1000.00 → 封顶 30.00」——**这是错的**。0.5% 下 1000.00 只返 5.00；单笔上限 30.00 要金额 ≥ 6 000.00 才触发。PRD US-CB-001 的 120.00 → 0.60 本身正确，验收用例若需要覆盖封顶，应改用 6 000.00 以上金额。
2. 首轮运行 4 个临时用例时我写的两条断言有误（把 0.5% 的 1000.00 期望成 30.00；断言异常 message 含 `INVALID_AMOUNT` 而实际 code 在独立字段）。**失败源于我的断言，不是产品缺陷**；修正断言后 4/4 通过。首次失败记录保留在上表日志的同一路径（已被覆盖），过程如实说明。

## 5. 仍然存在的缺口

1. 跨服务端到端（真实 HTTP：过账 → 评估 → 贷记 → 余额）仍未执行：环境无 Docker。
2. 金额 0 的 HTTP 层是否返回 400 与 `INVALID_AMOUNT` 响应体，只验证到服务层抛异常。
3. PRD 附录的余额基数（2550.75）与种子数据（2450.75）仍不一致，需勘误或补种子。
4. 上述临时用例目前不在交付提交中；若要沉淀为回归用例，需要在 develop 正式加入并随新提交发布。

## 6. 返工落地（2026-11-13 18:56）

临时用例已删除，改为三个正式用例进入提交 `574950a04b29f5831af0911d3b3db6cf7aec65d0`（test-only，`4 files changed, 71 insertions(+), 2 deletions(-)`，基于 `1a66fff`）：

| 用例 | 文件 | 覆盖 |
|------|------|------|
| `stacksPremiumAndExtraHalfPercentCampaigns` | `RewardServiceTest` | US-CB-009 / D-01 多活动叠加 |
| `grantsExtraHalfPercentWithinWindowAndCapsSingleTransaction` | `RewardServiceTest` | US-CB-001 费率与封顶、US-CB-002 窗口末日 |
| `evaluationRejectsZeroAmount` | `MarketingControllerTest` | US-CB-008 HTTP 400 `INVALID_AMOUNT` |

同提交还修正了 PRD US-CB-001 的余额基数（2550.75/2550.15 → 2450.75/2450.15，与 `V2__account_seed.sql` 一致），并在设计文档 18 的测试映射表补充上述三条用例。

执行结果：`mvn -B test`（`services/marketing-service`）**45 个用例全部通过**，exit 0，`BUILD SUCCESS`（`RewardServiceTest` 9→11、`MarketingControllerTest` 7→8）。

据此，ISS-01、ISS-02、ISS-03 已在**代码层面**关闭（不再是「无证据」），但 Teams 台账仍停在上一次提交的 `1a66fff` 与「未通过」的 verify 结论上；把新证据登记进台账需要 Owner 在页面退回任务后重新提交。
