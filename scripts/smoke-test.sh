#!/usr/bin/env bash
# 端到端冒烟测试：健康探针 → 登录 → 查询 → 入账 → 授权 → 报表 → 营销
set -euo pipefail

IAM=${IAM:-http://localhost:8081}
CUSTOMER=${CUSTOMER:-http://localhost:8082}
CARD=${CARD:-http://localhost:8083}
ACCOUNT=${ACCOUNT:-http://localhost:8084}
TRANSACTION=${TRANSACTION:-http://localhost:8085}
AUTHORIZATION=${AUTHORIZATION:-http://localhost:8086}
MARKETING=${MARKETING:-http://localhost:8087}
LEDGER=${LEDGER:-http://localhost:8088}
RECONCILIATION=${RECONCILIATION:-http://localhost:8089}
PAYMENT=${PAYMENT:-http://localhost:8091}
BILLING=${BILLING:-http://localhost:8092}
LIMIT=${LIMIT:-http://localhost:8093}
FX=${FX:-http://localhost:8094}

say() { printf '\n== %s\n' "$*"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print(json.dumps(d, ensure_ascii=False)[:400])"; }

say "1/8 健康探针"
for svc in "$IAM" "$CUSTOMER" "$CARD" "$ACCOUNT" "$TRANSACTION" "$AUTHORIZATION" "$MARKETING" "$LEDGER" "$RECONCILIATION" "$PAYMENT" "$BILLING" "$LIMIT" "$FX"; do
  status=$(curl -sf -m 5 "$svc/actuator/health" | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])")
  echo "  $svc -> $status"
done

say "2/8 登录（ADMIN001）"
curl -sf -m 5 -X POST "$IAM/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"userId":"admin001","password":"admin123"}' | json

say "3/8 账户 / 客户 / 卡片 / 产品与额度"
curl -sf -m 5 "$ACCOUNT/api/v1/accounts/00000000001" | json
curl -sf -m 5 "$CUSTOMER/api/v1/customers/000000001" | json
curl -sf -m 5 "$CARD/api/v1/cards/by-account/00000000001" | json
curl -sf -m 5 "$ACCOUNT/api/v1/account-products" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('products:', [(i['productCode'], i['maxCreditLimit']) for i in d['items']])"
curl -sf -m 5 "$ACCOUNT/api/v1/accounts/00000000006/credit-position" | json

say "4/8 日终入账（金额 1.00，交易号按时间生成，可重复执行）"
TXN_ID=$(printf "%016d" "$(date +%s)")
curl -sf -m 10 -X POST "$TRANSACTION/api/v1/postings" -H 'Content-Type: application/json' -d "{
  \"transactions\":[{
    \"transactionId\":\"$TXN_ID\",
    \"cardNumber\":\"3344556677889900\",
    \"typeCode\":\"01\",\"categoryCode\":\"0001\",\"source\":\"POS TERM\",
    \"description\":\"Smoke test purchase\",\"amount\":1.00,
    \"originalTimestamp\":\"2026-10-25T10:15:00\",
    \"merchantId\":\"800000001\",\"merchantName\":\"Smoke Test\",
    \"merchantCity\":\"Springfield\",\"merchantZip\":\"10001\"}]}" | json

say "5/8 授权决策（账户 1，可用额度 80.00）"
curl -sf -m 10 -X POST "$AUTHORIZATION/api/v1/authorizations" -H 'Content-Type: application/json' -d '{
  "cardNumber":"0500024453765740","transactionAmount":10.00,
  "transactionId":"SMOKE0000000001","merchantName":"Corner Grocery"}' | json
curl -sf -m 5 "$AUTHORIZATION/api/v1/pending-authorizations/00000000001" | json

say "5.1/8 资金冻结：创建、查额度、释放（账户 6）"
HOLD_REF="smoke-hold-$(date +%s)"
HOLD_ID=$(curl -sf -m 10 -X POST "$ACCOUNT/api/v1/accounts/00000000006/holds" \
  -H 'Content-Type: application/json' \
  -d "{\"amount\":25.00,\"referenceType\":\"AUTHORIZATION\",\"referenceId\":\"$HOLD_REF\"}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['holdId'])")
echo "  holdId=$HOLD_ID"
curl -sf -m 5 "$ACCOUNT/api/v1/accounts/00000000006/credit-position" | json
curl -sf -m 5 -X DELETE "$ACCOUNT/api/v1/accounts/00000000006/holds/$HOLD_ID" | json

say "5.2/8 过账与总账：创建批次 → 日记账 → 试算平衡 → 冲正"
POST_KEY="smoke-post-$(date +%s)"
BATCH_ID=$(curl -sf -m 10 -X POST "$TRANSACTION/api/v1/posting-batches" \
  -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"$POST_KEY\",\"typeCode\":\"CARD_PURCHASE\",\"accountId\":\"00000000006\",\"amount\":25.00,\"currency\":\"USD\",\"description\":\"Smoke posting\"}" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['batchId'])")
echo "  batchId=$BATCH_ID"
curl -sf -m 5 "$LEDGER/api/v1/journal-entries?sourceId=$BATCH_ID" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('journal:', [(i['sourceType'], i['status'], len(i['lines'])) for i in d['items']])"
curl -sf -m 5 "$LEDGER/api/v1/trial-balance?period=$(date +%Y-%m)" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('trial:', d['balanced'], d['totalDebit'], d['totalCredit'])"
curl -sf -m 10 -X POST "$TRANSACTION/api/v1/posting-batches/$BATCH_ID/reversal" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('reversal:', d['batchId'], d['status'], d['reversalOf'])"

say "5.3/8 对账与 EOD"
TODAY=$(date +%F)
curl -sf -m 15 -X POST "$RECONCILIATION/api/v1/reconciliation-runs" \
  -H 'Content-Type: application/json' -d "{\"businessDate\":\"$TODAY\"}" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('reconciliation:', d['status'], 'matched=', d['matched'], 'batches=', d['batchCount'], 'journals=', d['journalCount'], 'mismatch=', d['mismatchCount'])"
curl -sf -m 15 -X POST "$RECONCILIATION/api/v1/eod-runs" \
  -H 'Content-Type: application/json' -d "{\"businessDate\":\"$TODAY\"}" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('eod:', d['status'], d['reconciliationRunId'])"

say "5.4/8 支付 / 账单 / 限额 / 汇率"
PAY_KEY="smoke-pay-$(date +%s)"
PAY_ID=$(curl -sf -m 10 -X POST "$PAYMENT/api/v1/payment-orders" \
  -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"$PAY_KEY\",\"debtorAccountId\":\"00000000006\",\"creditorName\":\"Smoke Creditor\",\"creditorAccountNumber\":\"1234567890123456\",\"creditorBankCode\":\"BOFAUS3N\",\"amount\":10.00,\"currency\":\"USD\",\"paymentType\":\"ACH\",\"reference\":\"SMOKE\"}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['paymentId'])")
echo "  paymentId=$PAY_ID"
curl -sf -m 10 -X POST "$PAYMENT/api/v1/payment-orders/$PAY_ID/accept" \
  | python3 -c "import json,sys; print('  accept:', json.load(sys.stdin)['status'])"
curl -sf -m 10 -X POST "$PAYMENT/api/v1/payment-orders/$PAY_ID/settle" \
  | python3 -c "import json,sys; print('  settle:', json.load(sys.stdin)['status'])"
curl -sf -m 10 -X POST "$PAYMENT/api/v1/payment-orders/$PAY_ID/return" \
  | python3 -c "import json,sys; print('  return:', json.load(sys.stdin)['status'])"
curl -sf -m 10 -X POST "$BILLING/api/v1/statements/generate" \
  -H 'Content-Type: application/json' -d '{"accountId":"00000000004","period":"2026-10"}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('  statement:', d['status'], d['closingBalance'], d['minimumPayment'])"
curl -sf -m 5 "$LIMIT/api/v1/limits/check?scopeType=ACCOUNT&scopeId=00000000006&limitType=PURCHASE&currency=USD&amount=100" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('  limit:', d['allowed'], d['remainingAmount'])"
QUOTE_ID=$(curl -sf -m 10 -X POST "$FX/api/v1/fx/quotes" \
  -H 'Content-Type: application/json' -d '{"fromCurrency":"USD","toCurrency":"CNY","amount":10.00}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['quoteId'])")
curl -sf -m 10 -X POST "$FX/api/v1/fx/quotes/$QUOTE_ID/execute" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('  fx:', d['sourceAmount'], d['fromCurrency'], '->', d['convertedAmount'], d['toCurrency'])"

say "6/8 交易列表（前 2 条）"
curl -sf -m 5 "$TRANSACTION/api/v1/transactions?page=0&size=2" | json

say "7/8 交易报表（2026-09 闭区间）"
curl -sf -m 10 "$TRANSACTION/api/v1/reports/transactions?startDate=2026-09-01&endDate=2026-09-30" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print({k: d[k] for k in ('total','pageTotal','grandTotal')})"

say "8/8 营销：活动、活动中心、奖励与看板"
curl -sf -m 5 "$MARKETING/api/v1/campaigns" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('campaigns:', [(i['name'], i['status']) for i in d['items']])"
curl -sf -m 5 "$MARKETING/api/v1/eligibilities?accountId=00000000001&cardNumber=0500024453765740&customerId=000000001" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('offers:', [(i['name'], i['enrolled'], i['eligible']) for i in d['items']])"
curl -sf -m 5 "$MARKETING/api/v1/rewards?accountId=00000000001" | json
curl -sf -m 5 "$MARKETING/api/v1/marketing/summary" | json

printf '\n冒烟测试完成。\n'
