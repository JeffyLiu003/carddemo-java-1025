# 代码静态检查报告 · REQ-dcf89122

| 项 | 内容 |
|----|------|
| 代码版本 | `1a66fff39d5f6dd06b78ff868ae7347468873047` |
| 工具 | SpotBugs Maven Plugin 4.8.3.0（SpotBugs 引擎），通过 CLI 直接调用，**未修改任何 pom/构建文件** |
| 执行 | 2026-11-13 18:44 +08:00（transaction-service）、18:47 +08:00（marketing-service） |
| 命令 | `mvn -B -DskipTests compile com.github.spotbugs:spotbugs-maven-plugin:4.8.3.0:spotbugs`（分别在 `services/transaction-service`、`services/marketing-service`） |
| 退出码 | 两次均 0（`BUILD SUCCESS`） |
| 结论 | **通过**：本次改动类 `PostingService` 无任何告警；未发现高/中严重度缺陷；全部告警为既有的封装暴露类（低优先级） |

## 1. 结果统计

| 模块 | Bug 总数 | 分布 |
|------|---------|------|
| transaction-service | 49 | `EI_EXPOSE_REP2` 27、`EI_EXPOSE_REP` 21、`CT_CONSTRUCTOR_THROW` 1 |
| marketing-service | 26 | `EI_EXPOSE_REP2` 14、`EI_EXPOSE_REP` 12 |

本次修改的类 `com.carddemo.transaction.application.PostingService`：**0 条告警**。

`CT_CONSTRUCTOR_THROW` 命中对象为既有的 `com.carddemo.transaction.domain.model.TransactionRecord`（构造器抛异常导致对象逃逸风险），非本次引入。

## 2. 告警定性

- `EI_EXPOSE_REP` / `EI_EXPOSE_REP2`：getter/setter/构造器直接暴露可变对象引用。在本仓库（Spring Boot + JPA 实体 + 值对象）属架构性模式，扫描结果与改动前基线一致，**不构成本次交付的阻断问题**，也不由本次改动新增。
- `CT_CONSTRUCTOR_THROW`：`TransactionRecord` 在构造期校验并抛 `IllegalArgumentException`。属既有设计，风险为「构造失败时部分初始化的对象被 finalizer/子类使用」，与本次需求无关。

## 3. 工具选择与局限

1. 仓库 `pom.xml` 仅配置 JaCoCo，无 PMD/Checkstyle/SpotBugs 绑定；本次为满足「静态检查工具报告」，用 CLI 插件坐标运行 SpotBugs，不改变构建基线。
2. 因 `-DskipTests` 打包编译，本报告**不能**作为单测通过依据（单测结论见功能测试报告）。
3. 未执行依赖漏洞扫描（OWASP Dependency-Check 需下载 NVD 全量库，当前外网仅能访问 Maven 中央仓库且带宽约 10 KB/s，实测不可行），该限制记录在安全报告中。
4. 静态检查只覆盖两个受影响模块，未对全仓 29 个模块扫描（本次变更范围外）。

## 4. 原始报告位置

- `services/transaction-service/target/spotbugsXml.xml`
- `services/marketing-service/target/spotbugsXml.xml`
- 运行日志：`D:\teams-talent-demo\teams\.runtime\work-contexts\3dcf4860-50c4-448a-8975-f32053d29242.spotbugs.log`、`…spotbugs-marketing.log`
