# ADR-012: 支付清算通过过账批次落账

| 项 | 内容 |
|----|------|
| 编号 | ADR-012 |
| 日期 | 2026-10-26 |
| 状态 | 已接受 |
| 关联需求 | `docs/design/13-payments-billing-limits-fx.md` §2 |

## 背景

payment-service 在清算（settle）时需要真正减少付款账户余额。可选路径：

1. 直接调用 account-service 的 `/postings` 改余额；
2. 调用 transaction-service 创建 `PAYMENT` 过账批次；
3. 直接写 ledger 日记账。

需要兼顾：账务可追溯、借贷平衡、退汇可冲正、幂等重试。

## 候选方案

| 方案 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| A：直接改余额 | payment → account `/postings` | 链路最短 | 无借贷分录；退汇需另建补偿；审计弱 |
| B：过账批次（采用） | payment → transaction `POST /posting-batches`（type=PAYMENT） | 复用规则/分录/冲正/总账；幂等键清晰 | 依赖 transaction；多一跳 |
| C：直接写总账 | payment → ledger `/journal-entries` | 分录直接 | 账户余额与总账可能不一致；绕过业务规则 |

## 决策

采用方案 B：

1. 清算时以 `payment:<paymentId>` 为幂等键创建 `PAYMENT` 过账批次（accountEffect=DECREASE）。
2. 批次成功返回 `transactionBatchId` 后支付单置 SETTLED；重复 settle 直接返回。
3. 退汇时若已清算，调用 `POST /posting-batches/{batchId}/reversal` 冲正，再置 RETURNED。
4. 下游失败返回 503，支付单状态不变，可重试；不直接修改 account 余额或 ledger。

## 后果

- 支付链路依赖 transaction-service；其不可用时支付无法清算（失败关闭）。
- 总账同步仍为 transaction 的尽力而为策略（ADR-010），支付单不感知。
- 未来接真实清算网络时，只需替换 settle 的渠道适配器，落账路径不变。
- 当前未实现 cut-off、真实报文与退汇原因码；属演示级边界。

## 证据

- `docs/design/13-payments-billing-limits-fx.md` §2
- `services/payment-service/src/main/java/com/carddemo/payment/application/PaymentOrderService.java`
- `docs/design/adr/ADR-010-ledger-and-posting-separation.md`
