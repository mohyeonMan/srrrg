import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'https://jhhomehub.gonetis.com/srrrg-dev').replace(/\/+$/, '');
const RATES = parseRates(__ENV.WARM_RATES || '5,10,20');
const STAGE_DURATION = __ENV.WARM_STAGE_DURATION || '30s';
const RAMP_DURATION = __ENV.WARM_RAMP_DURATION || '5s';
const WARMUP_RATE = parsePositiveInteger(__ENV.WARMUP_RATE || '10', 'WARMUP_RATE');
const WARMUP_DURATION = __ENV.WARMUP_DURATION || '30s';
const DATA_LINKS = parsePositiveInteger(__ENV.WARM_DATA_LINKS || '20', 'WARM_DATA_LINKS');
const ORIGINAL_URL_BASE = __ENV.WARM_ORIGINAL_URL || 'https://example.com/srrrg-warm-cache';
const MAX_RATE = Math.max(...RATES);
const PRE_ALLOCATED_VUS = parsePositiveInteger(
  __ENV.WARM_PRE_ALLOCATED_VUS || String(Math.max(20, Math.ceil(MAX_RATE / 5))),
  'WARM_PRE_ALLOCATED_VUS',
);
const MAX_VUS = parsePositiveInteger(
  __ENV.WARM_MAX_VUS || String(Math.max(PRE_ALLOCATED_VUS, Math.min(200, MAX_RATE))),
  'WARM_MAX_VUS',
);
const WARMUP_PRE_ALLOCATED_VUS = Math.max(10, WARMUP_RATE * 2);
const WARMUP_MAX_VUS = Math.max(WARMUP_PRE_ALLOCATED_VUS, WARMUP_RATE * 4);

// 느린 요청이 어느 네트워크 구간에서 지연됐는지 결과 요약에 남김.
const redirectBlocked = new Trend('redirect_client_blocked', true);
const redirectConnecting = new Trend('redirect_client_connecting', true);
const redirectTlsHandshaking = new Trend('redirect_client_tls_handshaking', true);
const redirectSending = new Trend('redirect_client_sending', true);
const redirectWaiting = new Trend('redirect_client_waiting', true);
const redirectReceiving = new Trend('redirect_client_receiving', true);

// 워밍업 요청률에서 첫 측정 요청률로 이동할 때도 ramp를 적용함.
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
      preAllocatedVUs: WARMUP_PRE_ALLOCATED_VUS,
      maxVUs: WARMUP_MAX_VUS,
      gracefulStop: '5s',
    },
    warm_cache: {
      executor: 'ramping-arrival-rate',
      exec: 'warmCache',
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
    'http_req_duration{endpoint:redirect_warm_cache}': [
      { threshold: 'p(95)<100', abortOnFail: true, delayAbortEval: '1m' },
      { threshold: 'p(99)<250', abortOnFail: true, delayAbortEval: '1m' },
    ],
    dropped_iterations: [
      { threshold: 'rate<1', abortOnFail: true, delayAbortEval: '30s' },
      'count<10',
    ],
  },
};

export function setup() {
  console.log(
      `test configuration: scenario=warm-cache, baseUrl=${BASE_URL}, rates=${RATES.join(',')}, ` +
      `stageDuration=${STAGE_DURATION}, rampDuration=${RAMP_DURATION}, dataLinks=${DATA_LINKS}, ` +
      `warmupRate=${WARMUP_RATE}, warmupDuration=${WARMUP_DURATION}, ` +
      `preAllocatedVUs=${PRE_ALLOCATED_VUS}, maxVUs=${MAX_VUS}`,
  );

  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const links = [];

  // 서로 다른 row에 부하를 분산하고, 생성 과정에서 URL 위험 검사 캐시를 미리 채움.
  for (let i = 0; i < DATA_LINKS; i += 1) {
    const separator = ORIGINAL_URL_BASE.includes('?') ? '&' : '?';
    const originalUrl =
      `${ORIGINAL_URL_BASE}${separator}srrrg_warm_run=${runId}&link=${i}`;
    const response = http.post(
      `${BASE_URL}/api/links`,
      JSON.stringify({ originalUrl, expiresAt: null }),
      {
        headers: { 'Content-Type': 'application/json' },
        tags: { endpoint: 'link_create_setup' },
      },
    );

    const created = check(response, {
      'setup link create returns 201': (res) => res.status === 201,
    });
    if (!created) {
      cleanupLinks(links);
      fail(`setup link creation failed: index=${i}, status=${response.status}, body=${response.body}`);
    }

    const body = response.json();
    const validResponse = check(body, {
      'setup link has a valid code': (value) =>
        typeof value.code === 'string' && /^[0-9A-Za-z]{6}$/.test(value.code),
      'setup link has a short URL': (value) =>
        typeof value.shortUrl === 'string' && value.shortUrl.length > 0,
      'setup link has a secret key': (value) =>
        typeof value.secretKey === 'string' && value.secretKey.startsWith('srrrg_sk_'),
    });
    if (!validResponse) {
      cleanupLinks(links);
      fail(`setup link response was invalid: index=${i}`);
    }

    links.push({
      code: body.code,
      shortUrl: body.shortUrl,
      secretKey: body.secretKey,
    });
    console.log(`test link created: code=${body.code}, shortUrl=${body.shortUrl}`);
  }

  return { links };
}

export function warmup(data) {
  requestRedirect(data, 'redirect_warm_cache_warmup', false);
}

export function warmCache(data) {
  requestRedirect(data, 'redirect_warm_cache', true);
}

function requestRedirect(data, endpoint, recordMeasuredTimings) {
  // 전체 실행의 iteration 번호로 링크를 순환해 특정 row에 부하가 몰리지 않게 함.
  const link = data.links[exec.scenario.iterationInTest % data.links.length];
  const response = http.get(link.shortUrl, {
    redirects: 0,
    tags: { endpoint },
  });

  if (recordMeasuredTimings) {
    recordRedirectTimings(response);
  }

  check(response, {
    [`${endpoint} returns 302`]: (res) => res.status === 302,
  });
}

export function teardown(data) {
  cleanupLinks(data?.links || []);
}

function cleanupLinks(links) {
  for (const link of links) {
    const response = http.del(`${BASE_URL}/api/links/${link.code}`, null, {
      headers: { 'X-Srrrg-Secret-Key': link.secretKey },
      tags: { endpoint: 'link_delete_teardown' },
    });

    check(response, {
      'test link cleanup returns 200': (res) => res.status === 200,
    });
  }
}

function recordRedirectTimings(response) {
  redirectBlocked.add(response.timings.blocked);
  redirectConnecting.add(response.timings.connecting);
  redirectTlsHandshaking.add(response.timings.tls_handshaking);
  redirectSending.add(response.timings.sending);
  redirectWaiting.add(response.timings.waiting);
  redirectReceiving.add(response.timings.receiving);
}

function parseRates(value) {
  const rates = value.split(',').map((rate) => parsePositiveInteger(rate.trim(), 'WARM_RATES'));
  if (rates.length === 0) {
    throw new Error('WARM_RATES must contain at least one rate');
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
