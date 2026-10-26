#!/usr/bin/env bash
# 混沌/降级验证：停止营销服务，入账仍成功；恢复后再冲正。
# 仅用于本地 Compose 演示，会短暂停止 marketing-service 并产生一笔 1.00 过账。
set -euo pipefail
cd "$(dirname "$0")/.."

ACCOUNT=${ACCOUNT:-http://localhost:8084}
TXN=${TXN:-http://localhost:8085}
MARKETING_URL=${MARKETING_URL:-http://localhost:8087}
KEY="chaos-$(date +%s)"
BATCH=""

restore() {
  echo "==> 恢复 marketing-service"
  docker compose start marketing-service >/dev/null
  for i in $(seq 1 60); do
    curl -sf "$MARKETING_URL/actuator/health" >/dev/null && break
    sleep 1
  done
  curl -s "$MARKETING_URL/actuator/health" | python3 -c "import json,sys; print('marketing:', json.load(sys.stdin)['status'])" || true
  if [ -n "$BATCH" ]; then
    echo "==> 冲正测试批次 $BATCH"
    curl -s -X POST "$TXN/api/v1/posting-batches/$BATCH/reversal" \
      | python3 -c "import json,sys; d=json.load(sys.stdin); print('reversal:', d['status'])" || true
  fi
}
trap restore EXIT

echo "==> 账户 6 过账前额度"
curl -s "$ACCOUNT/api/v1/accounts/00000000006/credit-position" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('balance=', d['currentBalance'])"

echo "==> 停止 marketing-service"
docker compose stop marketing-service >/dev/null

echo "==> 营销不可用时创建过账（应成功）"
BATCH=$(curl -sf -X POST "$TXN/api/v1/posting-batches" -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"$KEY\",\"typeCode\":\"CARD_PURCHASE\",\"accountId\":\"00000000006\",\"amount\":1.00,\"currency\":\"USD\",\"description\":\"chaos test\"}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['batchId'])")
echo "  batchId=$BATCH"

echo "==> 账户 6 过账后额度（应只减少 1.00，奖励降级）"
curl -s "$ACCOUNT/api/v1/accounts/00000000006/credit-position" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('balance=', d['currentBalance'])"

echo "==> 混沌验证完成（退出时自动恢复并冲正）"
