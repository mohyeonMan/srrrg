import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';

const BASE_URL = (__ENV.BASE_URL || 'https://jhhomehub.gonetis.com/srrrg-dev').replace(/\/+$/, '');
const CACHE_MODE = (__ENV.LINK_CREATE_CACHE_MODE || 'hit').toLowerCase();
const RATES = parseRates(__ENV.LINK_CREATE_RATES || '1,2,5');
const STAGE_DURATION = __ENV.LINK_CREATE_STAGE_DURATION || '30s';
const RAMP_DURATION = __ENV.LINK_CREATE_RAMP_DURATION || '5s';
const WARMUP_RATE = parsePositiveInteger(__ENV.WARMUP_RATE || '1', 'WARMUP_RATE');
const WARMUP_DURATION = __ENV.WARMUP_DURATION || '30s';
const ORIGINAL_URL_BASE =
  __ENV.LINK_CREATE_ORIGINAL_URL || 'https://example.com/srrrg-link-create';
const MAX_RATE = Math.max(...RATES);
const PRE_ALLOCATED_VUS = Math.max(20, Math.ceil(MAX_RATE / 2));
const MAX_VUS = Math.max(PRE_ALLOCATED_VUS, Math.min(100, MAX_RATE * 10));

if (!['hit', 'miss'].includes(CACHE_MODE)) {
  throw new Error(`LINK_CREATE_CACHE_MODE must be hit or miss: ${CACHE_MODE}`);
}

const stages = RATES.flatMap((rate) => [
  { target: rate, duration: RAMP_DURATION },
  { target: rate, duration: STAGE_DURATION },
]);

export const options = {
  setupTimeout: '2m',
  teardownTimeout: '2m',
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      exec: 'warmup',
      rate: WARMUP_RATE,
      duration: WARMUP_DURATION,
      timeUnit: '1s',
      preAllocatedVUs: Math.max(20, WARMUP_RATE * 2),
      maxVUs: Math.max(40, WARMUP_RATE * 4),
      gracefulStop: '5s',
    },
    link_create: {
      executor: 'ramping-arrival-rate',
      exec: 'linkCreate',
      startTime: WARMUP_DURATION,
      startRate: WARMUP_RATE,
      timeUnit: '1s',
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
      stages,
      gracefulStop: '10s',
    },
  },
  thresholds: {
    checks: [{ threshold: 'rate==1', abortOnFail: true, delayAbortEval: '30s' }],
    http_req_failed: [{ threshold: 'rate<0.001', abortOnFail: true, delayAbortEval: '30s' }],
    [`http_req_duration{endpoint:link_create_cache_${CACHE_MODE}}`]: [
      { threshold: 'p(95)<300', abortOnFail: true, delayAbortEval: '1m' },
      { threshold: 'p(99)<500', abortOnFail: true, delayAbortEval: '1m' },
    ],
    dropped_iterations: [
      { threshold: 'rate<1', abortOnFail: true, delayAbortEval: '30s' },
      'count<10',
    ],
  },
};

export function setup() {
  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const hitUrl = `${ORIGINAL_URL_BASE}?run=${runId}&mode=hit`;
  console.log(
      `test configuration: scenario=link-create, cacheMode=${CACHE_MODE}, ` +
      `baseUrl=${BASE_URL}, rates=${RATES.join(',')}, stageDuration=${STAGE_DURATION}, ` +
      `rampDuration=${RAMP_DURATION}, warmupRate=${WARMUP_RATE}, ` +
      `warmupDuration=${WARMUP_DURATION}, preAllocatedVUs=${PRE_ALLOCATED_VUS}, ` +
      `maxVUs=${MAX_VUS}`,
  );

  if (CACHE_MODE === 'hit') {
    createAndDelete(hitUrl, 'link_create_setup_seed');
  }

  return { runId, hitUrl };
}

export function warmup(data) {
  const originalUrl = CACHE_MODE === 'hit'
    ? data.hitUrl
    : uniqueUrl(data.runId, 'warmup');
  createAndDelete(originalUrl, 'link_create_warmup');
}

export function linkCreate(data) {
  const originalUrl = CACHE_MODE === 'hit'
    ? data.hitUrl
    : uniqueUrl(data.runId, 'measured');
  createAndDelete(originalUrl, `link_create_cache_${CACHE_MODE}`);
}

function uniqueUrl(runId, purpose) {
  return `${ORIGINAL_URL_BASE}?run=${runId}&mode=miss&purpose=${purpose}` +
    `&scenario_iteration=${exec.scenario.iterationInTest}` +
    `&vu=${exec.vu.idInTest}`;
}

function createAndDelete(originalUrl, endpoint) {
  const response = http.post(
    `${BASE_URL}/api/links`,
    JSON.stringify({ originalUrl, expiresAt: null }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint },
    },
  );

  const created = check(response, {
    [`${endpoint} returns 201`]: (res) => res.status === 201,
  });
  if (!created) {
    fail(`link creation failed: endpoint=${endpoint}, status=${response.status}, body=${response.body}`);
  }

  const body = response.json();
  const validResponse = check(body, {
    [`${endpoint} has a valid code`]: (value) =>
      typeof value.code === 'string' && /^[0-9A-Za-z]{6}$/.test(value.code),
    [`${endpoint} has a secret key`]: (value) =>
      typeof value.secretKey === 'string' && value.secretKey.startsWith('srrrg_sk_'),
  });
  if (!validResponse) {
    fail(`link creation response was invalid: endpoint=${endpoint}`);
  }

  const deleted = http.del(`${BASE_URL}/api/links/${body.code}`, null, {
    headers: { 'X-Srrrg-Secret-Key': body.secretKey },
    tags: { endpoint: 'link_create_cleanup' },
  });
  check(deleted, {
    'created link cleanup returns 200': (res) => res.status === 200,
  });
}

function parseRates(value) {
  const rates = value.split(',').map((rate) =>
    parsePositiveInteger(rate.trim(), 'LINK_CREATE_RATES'));
  if (rates.length === 0) {
    throw new Error('LINK_CREATE_RATES must contain at least one rate');
  }
  return rates;
}

function parsePositiveInteger(value, name) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed < 1 || String(parsed) !== value) {
    throw new Error(`${name} must contain positive integers: ${value}`);
  }
  return parsed;
}
