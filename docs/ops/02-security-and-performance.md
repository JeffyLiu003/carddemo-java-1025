# 02 安全、性能与质量收口（P6）

> 范围：本地 Compose 演示环境的 NFR 目标、压测/混沌方法、安全核对与已知缺口。
> 结论：POC 可在隔离网络演示；生产化前必须完成鉴权、TLS、限流、密钥托管与审计接入。

## 1. NFR 目标（演示环境）

| 指标 | 目标 | 验证方式 |
|---|---|---|
| 查询接口 p95 | < 800ms | `scripts/perf/k6-smoke.js` |
| 写接口 p95 | < 1200ms | 压测脚本扩展 |
| 错误率 | < 1% | k6 `http_req_failed` |
| 核心服务可用性 | 单实例，允许重启 | `docker compose ps` / health |
| 过账幂等 | 重复请求不重复扣款 | 单测 + 冒烟 |
| 总账一致性 | 试算平衡 `Σ借=Σ贷` | 对账/EOD |
| 恢复时间（本地） | < 2 分钟 | 重启服务/Compose |

## 2. 性能测试

```bash
# 需安装 k6：brew install k6
k6 run scripts/perf/k6-smoke.js
# 指定入口
BASE_URL=http://localhost:8090 k6 run scripts/perf/k6-smoke.js
```

脚本为只读场景（账户、额度、交易、试算平衡、对账历史），避免压测污染账务。
写路径压测应在独立数据集上执行，并先关闭营销/总账同步或接受降级。

## 3. 混沌/降级

```bash
./scripts/chaos-test.sh
```

步骤：

1. 记录账户 6 额度；
2. 停止 `marketing-service`；
3. 创建 1.00 过账批次（应成功，奖励评估降级 WARN）；
4. 恢复营销服务并冲正测试批次。

验证点：资金链路不因营销不可用而失败；恢复后余额回归。

## 4. 覆盖率与架构约束

```bash
mvn verify                 # JaCoCo 报告：target/site/jacoco/index.html
mvn -pl services/account-service test   # ArchUnit 分层约束
```

- JaCoCo 已在父 POM 配置 `prepare-agent` + `report`，不设硬门槛（演示环境避免阻塞）。
- ArchUnit 示例位于 `services/account-service/src/test/java/com/carddemo/account/ArchitectureTest.java`：
  domain 不依赖 application/infrastructure/interfaces；application 不依赖 interfaces。
- 建议后续把 ArchUnit 复制到所有服务，并在 CI 设覆盖率门槛（核心域 ≥ 70%）。

## 5. 安全核对（OWASP Top 10 摘要）

| 风险 | 现状 | 处置 |
|---|---|---|
| A01 访问控制 | 资源服务未强制校验 token | 生产前接 API 网关/OAuth2；服务间 mTLS |
| A02 加密失败 | 本地 HTTP；PII 日志未全量脱敏 | 全站 TLS；字段级加密；日志脱敏 |
| A03 注入 | JPA 参数化；无字符串拼接 SQL | 保持；对外部查询做白名单 |
| A04 不安全设计 | 无限流/防重放；支付无 cut-off | 网关限流；幂等键已有；补 cut-off |
| A05 配置错误 | 默认密码/密钥仅演示 | 密钥托管；禁止默认值上线 |
| A06 脆弱依赖 | 依赖由 Spring Boot BOM 管理 | CI 执行依赖扫描（见下） |
| A07 认证失败 | 登录 PBKDF2；无 MFA | 接 MFA/设备指纹 |
| A08 数据完整性 | 过账/总账幂等与冲正 | 保持；补事件签名 |
| A09 日志监控 | 结构化日志 + Actuator | 接 SIEM/告警；审计服务已具备 |
| A10 SSRF | 下游 URL 由配置注入 | 白名单校验；禁止用户可控 URL |

依赖扫描建议：

```bash
mvn -q dependency:tree > /tmp/deps.txt
# 需联网/插件：
mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7
```

## 6. 已知边界

- 未做 API 网关（web-ui 同源代理 + BFF 承担边缘职责）；
- 未做多租户/多实体核算、账户级多币种余额；
- 未接真实 OpenSearch、MQ、SWIFT/ISO 20022 报文；
- 未做全链路压测环境、混沌平台、SIEM 与合规报表；
- 扩展服务默认不启动，使用 `docker compose --profile extended up -d`。
