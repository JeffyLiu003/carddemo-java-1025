#!/usr/bin/env bash
# 混沌/降级验证：停止总账服务，过账仍成功（总账同步失败只告警）；恢复后再冲正。
# 仅用于本地 Compose 演示，会短暂停止 ledger-service 并产生一笔 1.00 过账。
set -euo pipefail
cd "$(dirname "$0")/.."

ACCOUNT=${ACCOUNT:-http://localhost:8084}
TXN=${TXN:-http://localhost:8085}
LEDGER_URL=${LEDGER_URL:-http://localhost:8088}
KEY="chaos-$(date +%s)"
BATCH=""

sync_batch() {
  local batch_id=$1
  local synced="False"
  for _ in 1 2 3 4 5; do
    synced=$(curl -s -X POST "$TXN/api/v1/posting-batches/$batch_id/ledger-sync" \
      | python3 -c "import json,sys; print(json.load(sys.stdin).get('ledgerEntryId') is not None)" 2>/dev/null || true)
    [ "$synced" = "True" ] && break
    sleep 2
  done
  echo "  ledger-sync $batch_id: $synced"
}

restore() {
  echo "==> 恢复 ledger-service"
  docker compose start ledger-service >/dev/null
  for i in $(seq 1 60); do
    curl -sf "$LEDGER_URL/actuator/health" >/dev/null && break
    sleep 1
  done
  curl -s "$LEDGER_URL/actuator/health" | python3 -c "import json,sys; print('ledger:', json.load(sys.stdin)['status'])" || true
  if [ -n "$BATCH" ]; then
    echo "==> 补同步总账并冲正测试批次 $BATCH"
    sync_batch "$BATCH"
    REV_BATCH=$(curl -s -X POST "$TXN/api/v1/posting-batches/$BATCH/reversal" \
      | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['batchId'])" 2>/dev/null || true)
    echo "  reversal: $REV_BATCH"
    [ -n "$REV_BATCH" ] && sync_batch "$REV_BATCH"
  fi
}
trap restore EXIT

echo "==> 账户 6 过账前额度"
curl -s "$ACCOUNT/api/v1/accounts/00000000006/credit-position" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('balance=', d['currentBalance'])"

echo "==> 停止 ledger-service"
docker compose stop ledger-service >/dev/null

echo "==> 总账不可用时创建过账（应成功，ledgerEntryId 为空）"
BATCH=$(curl -sf -X POST "$TXN/api/v1/posting-batches" -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"$KEY\",\"typeCode\":\"CARD_PURCHASE\",\"accountId\":\"00000000006\",\"amount\":1.00,\"currency\":\"USD\",\"description\":\"chaos test\"}" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['batchId']); assert d['ledgerEntryId'] is None")
echo "  batchId=$BATCH"

echo "==> 账户 6 过账后额度（应只减少 1.00）"
curl -s "$ACCOUNT/api/v1/accounts/00000000006/credit-position" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('balance=', d['currentBalance'])"

echo "==> 混沌验证完成（退出时自动恢复总账并冲正）"
