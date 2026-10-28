# CardDemo Java 微服务版（carddemo-java-1025）

把 AWS CardDemo（CICS/COBOL 信用卡核心演示系统）迁移为基于 **DDD 限界上下文**的 Java 微服务工程。
本项目不是逐行翻译 COBOL，而是：先提取存量业务知识（L1–L5 五层模型 + 业务规则目录），
再按领域边界划分 27 个微服务（6 个既有业务域 + 营销、总账、对账、支付、账单、限额、汇率、信贷、风险、运营、渠道），最后用 Spring Boot 3 落地，并提供面向业务人员的 Web 界面与
Docker Compose 调试运行能力。

## 0. 当前代码基线

下表每项均与 `main` 分支代码核对过（服务目录、`docker-compose.yml`、`pom.xml`、surefire 报告、ADR 目录）：

| 维度 | 现状 |
|---|---|
| 默认分支 | `main`（历史演示分支 `dev-b`、`demo-b2`、`demo-b`、`dev`、`demo` 一并保留） |
| 技术栈 | Spring Boot 3.3.6 / Java 21 / PostgreSQL 16 / 原生 SPA |
| 服务 | 27 个 Java 微服务 + `web-ui`，Maven 聚合 28 个模块 |
| 数据库 | 25 个独立 database（`bff`、`search` 无状态，不建库） |
| 容器 | Compose 默认 17 个（15 个微服务 + web-ui + postgres），`extended` profile 追加 12 个 |
| 测试 | `mvn test` 816 个用例全绿（H2 + Flyway） |
| 决策 | 12 份 ADR（ADR-001–008、010–013；ADR-009 随旧营销模块移除） |
| 最新能力 | 高端卡期限活动消费返现（marketing-service，见 ADR-013 与 `docs/design/17-premium-cashback-campaign.md`） |

## 1. 架构总览

```mermaid
graph LR
    USER([业务人员]) --> WEB[web-ui :8090<br/>Web 界面 + 同源 API 代理]
    subgraph 核心域
      TXN[transaction-service :8085<br/>交易/过账/利息/账单支付/报表]
      LEDGER[ledger-service :8088<br/>科目/日记账/试算平衡/关账]
      PAY[payment-service :8091<br/>支付受理/筛查/清算/退汇]
      AUTH[authorization-service :8086<br/>授权决策/待授权/欺诈]
      ACCT[account-service :8084<br/>产品/额度/余额/冻结]
      CARD[card-service :8083<br/>卡与卡-账户-客户映射]
    end
    subgraph 支撑/通用域
      CUST[customer-service :8082<br/>客户主数据]
      IAM[iam-service :8081<br/>登录/用户]
      MKT[marketing-service :8087<br/>高端卡返现活动]
      RECON[reconciliation-service :8089<br/>对账/差异处理/EOD]
      BILL[billing-service :8092<br/>账单/利息/最低还款]
      LIMIT[limit-service :8093<br/>限额/用量]
      FX[fx-service :8094<br/>汇率/报价/换汇]
      LENDING[loan-service :8095<br/>信贷/还款计划]
      RISK[fraud :8096 / aml :8097<br/>dispute :8098 / collections :8099]
      OPS[batch :8100 / reporting :8101<br/>notification :8102 / document :8103<br/>workflow :8104 / audit :8105<br/>reference-data :8106]
      BFF[bff-service :8107<br/>客户 360 聚合]
      SEARCH[search-service :8108<br/>统一搜索]
    end
    DB[(PostgreSQL 16<br/>25 个独立 database)]

    WEB -. /api/** 代理 .-> IAM
    WEB -.-> CUST
    WEB -.-> CARD
    WEB -.-> ACCT
    WEB -.-> TXN
    WEB -.-> AUTH
    WEB -.-> MKT
    WEB -.-> LEDGER
    WEB -.-> RECON
    WEB -.-> PAY
    WEB -.-> BILL
    WEB -.-> LIMIT
    WEB -.-> FX
    WEB -.-> BFF
    WEB -.-> SEARCH
    CARD --> TXN
    ACCT --> TXN
    CARD --> AUTH
    ACCT --> AUTH
    CUST -.-> AUTH
    IAM -. token 契约（POC 未强制） .-> TXN
    TXN -. 入账后返现评估 .-> MKT
    TXN -. 过账后同步日记账 .-> LEDGER
    RECON -. 读取批次/日记账 .-> TXN
    RECON -. 读取日记账/试算平衡 .-> LEDGER
    PAY -. 清算过账 .-> TXN
    PAY -. 账户校验 .-> ACCT
    BILL -. 账户/产品/还款 .-> ACCT
    BFF -. 聚合 .-> ACCT
    BFF -. 聚合 .-> CARD
    BFF -. 聚合 .-> TXN
    SEARCH -. 查询 .-> CUST
    SEARCH -. 查询 .-> TXN
    MKT -. 返现贷记 .-> ACCT
    TXN --> DB
    LEDGER --> DB
    RECON --> DB
    PAY --> DB
    BILL --> DB
    LIMIT --> DB
    FX --> DB
    LENDING --> DB
    RISK --> DB
    OPS --> DB
    AUTH --> DB
    ACCT --> DB
    CARD --> DB
    CUST --> DB
    IAM --> DB
    MKT --> DB
```

| 服务 | 端口 | 数据库 | 对应遗留程序 |
|---|---|---|---|
| web-ui | 8090 → 容器 8080 | —（静态 SPA + 代理） | 对应 BMS 屏幕（COSGN00C/COMEN01C/COADM01C 等） |
| iam-service | 8081 | `carddemo_iam` | COSGN00C、COADM01C、COMEN01C、COUSR00C–03C |
| customer-service | 8082 | `carddemo_customer` | CUSTDAT、CBCUS01C、COACTUPC（客户部分） |
| card-service | 8083 | `carddemo_card` | CARDDAT、CCXREF、COCRDLIC/SLC/UPC |
| account-service | 8084 | `carddemo_account` | ACCTDAT、COACTVWC、COACTUPC（账户部分）、CBTRN02C 账户段、CBACT04C 结算、COBIL00C 扣款；新增产品目录、资金冻结与可用额度 |
| transaction-service | 8085 | `carddemo_transaction` | TRANSACT、DALYTRAN、TCATBALF、CBTRN01C/02C/03C、CBACT04C、COBIL00C、COTRN00C/01C/02C、CORPT00C、COTRTLIC；新增过账引擎（类型/规则/批次/冲正） |
| ledger-service | 8088 | `carddemo_ledger` | **新增能力**（存量无总账）：科目表、复式日记账、试算平衡、期间关账 |
| reconciliation-service | 8089 | `carddemo_reconciliation` | **新增能力**：过账批次 ↔ 总账日记账对账、差异处理、EOD 作业入口 |
| payment-service | 8091 | `carddemo_payment` | **新增能力**：支付指令受理、筛查、清算（过账）、退汇 |
| billing-service | 8092 | `carddemo_billing` | **新增能力**：账单生成、利息/最低还款、账单支付 |
| limit-service | 8093 | `carddemo_limit` | **新增能力**：客户/账户/渠道限额、用量与校验 |
| fx-service | 8094 | `carddemo_fx` | **新增能力**：汇率维护、报价、换汇记录 |
| marketing-service | 8087 | `carddemo_marketing` | **新增能力**：高端卡期限活动消费返现（白金/金卡、活动 1–24 个月、单笔/客户/预算上限、报名与效果看板） |
| loan-service | 8095 | `carddemo_loan` | **新增能力**：贷款申请、审批、放款、等额本息计划、还款 |
| fraud-service | 8096 | `carddemo_fraud` | **新增能力**：欺诈规则评分、警报与处置 |
| aml-service | 8097 | `carddemo_aml` | **新增能力**：制裁/名单筛查、AML 警报 |
| dispute-service | 8098 | `carddemo_dispute` | **新增能力**：争议案件、临时入账、chargeback |
| collections-service | 8099 | `carddemo_collections` | **新增能力**：逾期分桶、承诺还款、结案 |
| batch-service | 8100 | `carddemo_batch` | **新增能力**：作业定义与 EOD 执行（计息/对账） |
| reporting-service | 8101 | `carddemo_reporting` | **新增能力**：交易汇总、试算平衡报表 |
| notification-service | 8102 | `carddemo_notification` | **新增能力**：模板、通知与发送状态 |
| document-service | 8103 | `carddemo_document` | **新增能力**：文档元数据、文本内容、归档 |
| workflow-service | 8104 | `carddemo_workflow` | **新增能力**：流程定义、任务审批/拒绝 |
| audit-service | 8105 | `carddemo_audit` | **新增能力**：追加式审计事件与检索 |
| reference-data-service | 8106 | `carddemo_reference` | **新增能力**：币种/国家/假日/产品类别 |
| bff-service | 8107 | —（无状态） | **新增能力**：客户 360 聚合 |
| search-service | 8108 | —（无状态） | **新增能力**：客户/交易统一搜索 |
| authorization-service | 8086 | `carddemo_authorization` | COPAUA0C、COPAUS0C/1C/2C、CBPAUP0C、IMS PAUTSUM0/PAUTDTL1、DB2 AUTHFRDS |

## 2. 目录结构

```
carddemo-java-1025/
├── pom.xml                      # Maven 聚合父 POM（Spring Boot 3.3.6 / Java 21）
├── docker-compose.yml           # PostgreSQL + 15 个默认微服务 + Web 界面；extended profile 追加 12 个
├── docker/postgres/init/        # 25 个 database 初始化
├── scripts/build-and-up.sh      # 打包 + 构建镜像 + 启动
├── scripts/smoke-test.sh        # 端到端冒烟测试
├── scripts/chaos-test.sh        # 混沌/降级验证：停总账后过账仍成功
├── scripts/perf/k6-smoke.js     # k6 只读压测（p95 < 800ms、错误率 < 1%）
├── docs/
│   ├── legacy/                  # 存量知识：清单、五层模型、规则目录、数据字典、术语
│   ├── design/                  # DDD 设计：子域/上下文/拆分/API/追踪矩阵/ADR
│   └── ops/                     # Docker 运行手册、安全与性能收口
└── services/
    ├── iam-service/
    ├── customer-service/
    ├── card-service/
    ├── account-service/
    ├── transaction-service/
    ├── ledger-service/           # 总账：科目/日记账/试算平衡/关账（新增）
    ├── reconciliation-service/   # 对账/EOD：批次↔日记账、差异处理（新增）
    ├── payment-service/          # 支付：受理/筛查/清算/退汇（新增）
    ├── billing-service/          # 账单：生成/利息/最低还款/支付（新增）
    ├── limit-service/            # 限额：额度/用量/校验（新增）
    ├── fx-service/               # 汇率：报价/换汇（新增）
    ├── marketing-service/        # 高端卡返现活动（新增）
    ├── loan-service/             # 信贷：申请/审批/放款/还款（新增）
    ├── fraud-service/            # 欺诈评分/警报（新增）
    ├── aml-service/              # 制裁筛查/AML 警报（新增）
    ├── dispute-service/          # 争议/chargeback（新增）
    ├── collections-service/      # 逾期/催收（新增）
    ├── batch-service/            # 批处理/EOD（新增）
    ├── reporting-service/        # 报表（新增）
    ├── notification-service/     # 通知（新增）
    ├── document-service/         # 文档（新增）
    ├── workflow-service/         # 工作流（新增）
    ├── audit-service/            # 审计（新增）
    ├── reference-data-service/   # 参考数据（新增）
    ├── bff-service/              # 客户 360 聚合（新增）
    ├── search-service/           # 统一搜索（新增）
    ├── authorization-service/
    └── web-ui/                  # 业务界面（静态 SPA）+ 同源 API 代理
```

除 `web-ui` 外，每个 Java 微服务采用同一分层：

```
src/main/java/com/carddemo/<context>/
├── interfaces/rest   # Controller + DTO
├── application       # 用例编排、事务边界
├── domain            # 聚合根、领域服务、仓储端口、异常
└── infrastructure    # JPA 适配器、下游 REST 客户端、配置
```

## 3. 快速开始

前置：JDK 21、Maven 3.9+、Docker + Compose v2。

```bash
git clone https://github.com/JeffyLiu003/carddemo-java-1025.git
cd carddemo-java-1025
./scripts/build-and-up.sh          # mvn package + docker compose up -d --build
docker compose ps
./scripts/smoke-test.sh            # 端到端验证（健康 → 登录 → 查询 → 入账 → 授权 → 报表 → 支付/对账 → 限额/FX/返现 → BFF/搜索）
```

打开 **http://localhost:8090/** 即为业务界面（登录账号已预填，直接登录）。

> 当前环境的 Docker Hub 不可达，镜像基于本机已有的 `eclipse-temurin:21.0.8_9-jdk` 与
> `postgres:16-alpine` 构建，因此镜像构建前必须先在宿主机执行 `mvn package`（脚本已包含）。
> 如果你的网络可访问 Docker Hub，可将各服务 `Dockerfile` 的首行改为 `eclipse-temurin:21-jre`
> 并恢复多阶段构建。

全部单元/集成测试：

```bash
mvn test        # 816 个用例，H2 + Flyway，多模块并行
```

按需追加能力与验证脚本：

```bash
# extended profile：再启动 12 个服务（信贷/风控/运营），共 29 个容器
docker compose --profile extended up -d

# 混沌/降级：停掉 ledger-service 后过账仍成功（总账同步失败只告警），恢复后再冲正
./scripts/chaos-test.sh

# 只读压测（需 brew install k6）：p95 < 800ms、错误率 < 1%
k6 run scripts/perf/k6-smoke.js
```

## 3.1 Web 界面（推荐）

打开 **http://localhost:8090/**（宿主机 8080 已被其他程序占用，故映射到 8090；容器内仍为 8080）。

| 页面 | 能力 | 对应遗留屏幕/程序 |
|---|---|---|
| 登录 | 账号已预填，管理员/普通用户切换 | COSGN00C |
| 首页 | 按角色展示可用功能入口 | COMEN01C / COADM01C |
| 账户视图 | 账户 + 客户 + 卡片组合展示、产品/额度/冻结/可用额度、创建与释放 hold | COACTVWC |
| 过账 | 过账类型、幂等批次、借贷分录、冲正（钱路径新增） | 新增能力 |
| 总账 | 科目表、日记账、试算平衡、期间关账、手工记账（钱路径新增） | 新增能力 |
| 对账与 EOD | 按日对账、差异明细与处理、EOD 作业历史（管理员，钱路径新增） | 新增能力 |
| 支付 | 创建支付、筛查、接受、清算、退汇（P2 新增） | 新增能力 |
| 账单 | 生成账单、查看利息/最低还款/到期日、全额支付（P2 新增） | 新增能力 |
| 限额 | 创建/调整限额、占用/释放、可用性检查（管理员，P2 新增） | 新增能力 |
| 汇率 | 汇率维护、报价、执行换汇、换汇记录（P2 新增） | 新增能力 |
| 高端返现 | 白金/金卡期限活动、报名、消费返现、返现失败重试 | 新增能力 |
| 返现活动 | 创建 1–24 个月活动、产品圈选、发布/暂停/结束、效果（管理员） | 新增能力 |
| 客户 360 | BFF 聚合账户/卡片/客户/交易/权益，统一搜索（P5 新增） | 新增能力 |
| 贷款 | 申请、审批/拒绝、放款、还款计划、还款（P3 新增） | 新增能力 |
| 风险 | 欺诈评分/警报、AML 筛查/警报、争议、催收（P3 新增） | 新增能力 |
| 运营 | 批处理、报表、通知、文档、工作流、审计、参考数据（P4 新增） | 新增能力 |
| 信用卡 | 按账户/客户筛选、分页、详情与维护（卡面名/状态/到期） | COCRDLIC / COCRDSLC / COCRDUPC |
| 交易查询 | 交易号前缀/卡号/账户过滤、分页、详情 | COTRN00C / COTRN01C |
| 交易录入 | 类型/类别联动选择、完整商户信息、自动生成交易号 | COTRN02C |
| 账单支付 | 查询欠款 → 确认 → 全额还款并回显新余额 | COBIL00C |
| 交易报表 | 日期区间统计、区间合计、按卡合计 | CORPT00C / CBTRN03C |
| 授权管理 | 待授权汇总、明细、标记欺诈、过期清理 | COPAUS0C/1C/2C、CBPAUP0C |
| 用户管理 | 列表、新增、编辑、删除（仅管理员） | COUSR00C–03C |

| 演示账号 | 密码 | 类型 |
|---|---|---|
| `ADMIN001` | `admin123` | 管理员（已预填） |
| `USER0001` | `user1234` | 普通用户 |

前端为无构建依赖的原生 SPA（`services/web-ui/src/main/resources/static`），所有 `/api/**` 请求由
web-ui 同源代理到对应微服务，浏览器无需处理 CORS。界面不做敏感信息展示：卡号完整显示仅用于演示，
SSN 始终为掩码，CVV 不返回。

## 3.2 API 控制台（Swagger UI）

每个微服务的根路径会 302 到自己的 Swagger UI，可用于接口调试：

| 服务 | Swagger UI | OpenAPI |
|---|---|---|
| iam | http://localhost:8081/ | http://localhost:8081/v3/api-docs |
| customer | http://localhost:8082/ | http://localhost:8082/v3/api-docs |
| card | http://localhost:8083/ | http://localhost:8083/v3/api-docs |
| account | http://localhost:8084/ | http://localhost:8084/v3/api-docs |
| transaction | http://localhost:8085/ | http://localhost:8085/v3/api-docs |
| ledger | http://localhost:8088/ | http://localhost:8088/v3/api-docs |
| reconciliation | http://localhost:8089/ | http://localhost:8089/v3/api-docs |
| payment | http://localhost:8091/ | http://localhost:8091/v3/api-docs |
| billing | http://localhost:8092/ | http://localhost:8092/v3/api-docs |
| limit | http://localhost:8093/ | http://localhost:8093/v3/api-docs |
| fx | http://localhost:8094/ | http://localhost:8094/v3/api-docs |
| marketing | http://localhost:8087/ | http://localhost:8087/v3/api-docs |
| loan | http://localhost:8095/ | http://localhost:8095/v3/api-docs |
| fraud | http://localhost:8096/ | http://localhost:8096/v3/api-docs |
| aml | http://localhost:8097/ | http://localhost:8097/v3/api-docs |
| dispute | http://localhost:8098/ | http://localhost:8098/v3/api-docs |
| collections | http://localhost:8099/ | http://localhost:8099/v3/api-docs |
| batch | http://localhost:8100/ | http://localhost:8100/v3/api-docs |
| reporting | http://localhost:8101/ | http://localhost:8101/v3/api-docs |
| notification | http://localhost:8102/ | http://localhost:8102/v3/api-docs |
| document | http://localhost:8103/ | http://localhost:8103/v3/api-docs |
| workflow | http://localhost:8104/ | http://localhost:8104/v3/api-docs |
| audit | http://localhost:8105/ | http://localhost:8105/v3/api-docs |
| reference | http://localhost:8106/ | http://localhost:8106/v3/api-docs |
| bff | http://localhost:8107/ | http://localhost:8107/v3/api-docs |
| search | http://localhost:8108/ | http://localhost:8108/v3/api-docs |
| authorization | http://localhost:8086/ | http://localhost:8086/v3/api-docs |

登录接口 `POST /api/v1/auth/login` 的请求体已预填演示账号；交易、授权、账单支付、入账、利息等
接口也预填了真实演示数据。OpenAPI 描述由代码注解生成。

> 注意：Swagger UI 是**按服务**的 API 控制台；Web 界面（8090）才是面向业务人员的统一入口。
> POC 未强制校验 token（ADR-007），两个入口都只应在本机/隔离网络使用。

## 4. 关键业务场景（curl）

```bash
# 登录（管理员）
curl -s -X POST localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"admin001","password":"admin123"}'

# 账户 / 客户 / 卡（组合出遗留 COACTVWC 视图）
curl -s localhost:8084/api/v1/accounts/00000000001
curl -s localhost:8082/api/v1/customers/000000001
curl -s localhost:8083/api/v1/cards/by-account/00000000001

# 日终入账（卡 3344556677889900 → 账户 6）
curl -s -X POST localhost:8085/api/v1/postings -H 'Content-Type: application/json' -d '{
  "transactions":[{"transactionId":"0000000000000100","cardNumber":"3344556677889900",
  "typeCode":"01","categoryCode":"0001","source":"POS TERM","description":"Purchase",
  "amount":25.00,"originalTimestamp":"2026-10-25T10:15:00","merchantId":"800000001",
  "merchantName":"Smoke Test","merchantCity":"Springfield","merchantZip":"10001"}]}'

# 授权（账户 1：额度 2020、欠款 1940 → 可用 80）
curl -s -X POST localhost:8086/api/v1/authorizations -H 'Content-Type: application/json' -d '{
  "cardNumber":"0500024453765740","transactionAmount":50.00,
  "transactionId":"TXN000000000001","merchantName":"Corner Grocery"}'

# 账单支付（全额还款）
curl -s -X POST localhost:8085/api/v1/bill-payments -H 'Content-Type: application/json' \
  -d '{"accountId":"00000000001","confirm":true}'

# 交易报表（2026-09，闭区间）
curl -s "localhost:8085/api/v1/reports/transactions?startDate=2026-09-01&endDate=2026-09-30"

# 过账（幂等批次，账户 6；CARD_PURCHASE = DR 1100 / CR 2100）
curl -s -X POST localhost:8085/api/v1/posting-batches -H 'Content-Type: application/json' -d '{
  "idempotencyKey":"demo-posting-0001","typeCode":"CARD_PURCHASE","accountId":"00000000006",
  "amount":25.00,"currency":"USD","description":"Demo purchase"}'

# 总账试算平衡（2026-10）
curl -s "localhost:8088/api/v1/trial-balance?period=2026-10"

# 对账（业务日期当天）与 EOD
curl -s -X POST localhost:8089/api/v1/reconciliation-runs \
  -H 'Content-Type: application/json' -d '{"businessDate":"2026-10-26"}'
curl -s -X POST localhost:8089/api/v1/eod-runs \
  -H 'Content-Type: application/json' -d '{"businessDate":"2026-10-26"}'
```

## 5. 设计要点

- **限界上下文优先**：账户与客户不再同事务更新（遗留 `COACTUPC` 一屏两聚合），账单支付由
  transaction-service 编排、account-service 执行扣款，授权上下文独立持有待授权汇总。见 ADR-001/004。
- **标识符零填充字符串 + BigDecimal**：`accountId` 11 位、`cardNumber` 16 位、`customerId` 9 位、
  `transactionId` 16 位；金额统一 `numeric(12,2)`。见 ADR-002。
- **每服务一库**：禁止跨库 JOIN，跨域只走 REST；下游 404 表示"不存在"、5xx/超时表示"无法判断"（失败关闭）。
- **乐观锁替代记录锁**：`@Version` 替代 `READ UPDATE` + 快照比对，冲突返回 409。见 ADR-006。
- **缺陷修复**（均有记录与测试）：CVV 不被卡维护覆盖、过期授权清理回退信用余额、
  可用额度同时扣减账户欠款与授权占用、密码 PBKDF2 存储。见规则目录中的 `NR-*`。
- **过账与总账（新增）**：过账批次按类型规则生成借贷分录，账户余额变更与分录同事务、总账同步尽力而为；
  冲正生成反向批次并保留原记录；总账按 `(sourceType, sourceId)` 幂等，试算平衡与期间关账可查。
  见 `docs/design/11-money-path-slice-a2.md`。
- **对账与 EOD（新增）**：按业务日期把过账批次与总账 `POSTING_BATCH` 日记账逐笔匹配，
  识别缺失、金额不符与孤儿分录；差异可登记处理；EOD 触发对账并留痕。见 `docs/design/12-reconciliation-and-eod.md`。
- **支付与计费（新增）**：支付指令受理→筛查→接受→清算（过账批次）→退汇（冲正）；
  账单按账户/期间生成，计算利息与最低还款并支持全额支付；限额与 FX 独立服务。见 `docs/design/13-payments-billing-limits-fx.md`。
- **高端卡返现（新增）**：按产品等级圈选（PLATINUM/GOLD），活动期限 1–24 个月；报名后活动期内消费按比例返现，
  受单笔/客户/预算上限约束；返现通过 account 贷记入账，失败 PENDING 可重试。见 `docs/design/17-premium-cashback-campaign.md`。

## 6. 文档

| 入口 | 说明 |
|---|---|
| [docs/README.md](docs/README.md) | 全部文档索引 |
| [docs/legacy/02-five-level-model.md](docs/legacy/02-five-level-model.md) | L1–L5 业务模型（带证据） |
| [docs/legacy/03-business-rule-catalog.md](docs/legacy/03-business-rule-catalog.md) | 业务规则 → 实现落点 → 状态 |
| [docs/design/02-bounded-contexts.md](docs/design/02-bounded-contexts.md) | 27 个限界上下文 |
| [docs/design/04-context-map.md](docs/design/04-context-map.md) | 上下文映射图、集成契约与防腐层 |
| [docs/design/05-api-catalog.md](docs/design/05-api-catalog.md) | 全部 API 契约与种子数据 |
| [docs/design/06-traceability-matrix.md](docs/design/06-traceability-matrix.md) | 44 个遗留程序的去向（总账/对账/支付/账单为新增能力） |
| [docs/design/07-java-conventions.md](docs/design/07-java-conventions.md) | 编码、分层、配置与测试统一基线 |
| [docs/design/09-scale-up-blueprint.md](docs/design/09-scale-up-blueprint.md) | 规模化蓝图与分阶段路线图 |
| [docs/design/10-money-path-slice-a1.md](docs/design/10-money-path-slice-a1.md) | A1：账户产品化、冻结、可用额度 |
| [docs/design/11-money-path-slice-a2.md](docs/design/11-money-path-slice-a2.md) | A2：过账引擎、总账、试算平衡与前台 |
| [docs/design/12-reconciliation-and-eod.md](docs/design/12-reconciliation-and-eod.md) | A1.4：对账、差异处理与 EOD 作业 |
| [docs/design/13-payments-billing-limits-fx.md](docs/design/13-payments-billing-limits-fx.md) | P2：支付、账单、限额与 FX |
| [docs/design/14-risk-credit-collections.md](docs/design/14-risk-credit-collections.md) | P3：信贷、欺诈、AML、争议、催收、批处理 |
| [docs/design/15-operations-support.md](docs/design/15-operations-support.md) | P4：报表、通知、文档、工作流、审计、参考数据 |
| [docs/design/16-channel-and-quality.md](docs/design/16-channel-and-quality.md) | P5/P6：BFF、搜索、门户、性能、混沌与安全 |
| [docs/design/17-premium-cashback-campaign.md](docs/design/17-premium-cashback-campaign.md) | 高端卡返现活动：需求、影响、架构、Review 与测试 |
| [docs/design/adr/README.md](docs/design/adr/README.md) | 12 个架构决策（ADR-001–008、010–013） |
| [docs/ops/01-docker-runbook.md](docs/ops/01-docker-runbook.md) | Docker 调试运行手册 |
| [docs/ops/02-security-and-performance.md](docs/ops/02-security-and-performance.md) | 安全、性能（k6）、混沌与质量收口 |

## 7. 已知限制（POC 范围）

1. **未强制鉴权**：iam 已签发 HMAC token，资源服务侧校验留待网关（ADR-007），
   Web 界面（8090）与各服务端口均不得暴露公网。
2. **无分布式事务**：账单支付/入账采用"本地事务 + 同步调用 + 幂等键"，Saga/Outbox 见 ADR-005。
3. **报表只迁移数据口径**，不迁移 133 字节打印排版；对账单/分行迁移/MQ 适配器为延后项。
4. **种子数据为归一化样例**：遗留 ASCII 数据存在 EBCDIC 过穿孔字符，真实迁移须按数据字典解码。
5. 单账户并发授权使用乐观锁，热点账户需重试；已提供 k6 只读压测（`scripts/perf/k6-smoke.js`，
   NFR 目标见 `docs/ops/02-security-and-performance.md`），分区与写路径压测仍待补（未解决项 U1）。
6. **总账为新增能力**：过账后同步调用总账，失败只告警（`ledgerEntryId` 为空），尚无 Outbox 重试；
   试算平衡按发生额汇总，不包含期初余额；期间关账暂不支持反关账。
7. **对账为新增能力**：当前只做“过账批次 ↔ 总账日记账”与试算平衡检查；账户余额 vs 总账 1100
   需要按账户的子账映射（未实现）。EOD 目前只编排对账，计息/账单/总账结转待扩展。
8. **支付为演示级**：无真实 ISO 20022/SWIFT/ACH 报文、清算网络与 cut-off；制裁筛查为硬编码名单。
9. **账单/限额/FX 为演示级**：账单基于当前账户快照生成，无历史周期、滞纳金与分期；
   限额尚未接入授权链路；FX 仅支持直接货币对，无三角套算与敞口管理。
10. **P3/P4/P5 为演示级**：贷款/欺诈/AML/争议/催收为单聚合状态机；批处理同步调用、无调度器；
    报表/通知/文档/工作流/审计/参考数据为轻量实现；搜索为实时过滤，无 OpenSearch 索引；
    BFF 串行聚合，未做并行/缓存。
11. **高端返现为演示级**：预算/客户上限为单实例保护；报名未二次校验 customerId 与卡归属；
    本期不含积分、A/B、复杂客群 DSL；返现失败保留 PENDING 可重试。
12. **扩展服务默认不启动**：为控制内存，P3/P4 的 12 个服务放在 Compose `extended` profile，
    使用 `docker compose --profile extended up -d` 启动；默认运行 17 个容器。
