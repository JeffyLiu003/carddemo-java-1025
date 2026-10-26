import http from 'k6/http';
import { check, sleep } from 'k6';

// 运行：k6 run scripts/perf/k6-smoke.js
// 可选：BASE_URL=http://localhost:8090 k6 run scripts/perf/k6-smoke.js
export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8090';
const headers = { 'Content-Type': 'application/json' };

export default function () {
  const account = http.get(`${BASE}/api/v1/accounts/00000000001`);
  check(account, { 'account 200': (r) => r.status === 200 });

  const position = http.get(`${BASE}/api/v1/accounts/00000000001/credit-position`);
  check(position, { 'position 200': (r) => r.status === 200 });

  const transactions = http.get(`${BASE}/api/v1/transactions?page=0&size=5`);
  check(transactions, { 'transactions 200': (r) => r.status === 200 });

  const trial = http.get(`${BASE}/api/v1/trial-balance?period=2026-10`);
  check(trial, { 'trial 200': (r) => r.status === 200 });

  // 只读检查，避免压测污染账务；写路径由 smoke-test 覆盖。
  const recon = http.get(`${BASE}/api/v1/reconciliation-runs?page=0&size=1`);
  check(recon, { 'reconciliation 200': (r) => r.status === 200 });

  sleep(1);
}
