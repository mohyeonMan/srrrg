import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'https://jhhomehub.gonetis.com/srrrg-dev').replace(/\/+$/, '');
const REDIRECT_RATE = positiveInteger(__ENV.MIXED_REDIRECT_RATE || '500', 'MIXED_REDIRECT_RATE');
const HIT_CREATE_RATE = positiveInteger(__ENV.MIXED_HIT_CREATE_RATE || '4', 'MIXED_HIT_CREATE_RATE');
const MISS_CREATE_RATE = positiveInteger(__ENV.MIXED_MISS_CREATE_RATE || '1', 'MIXED_MISS_CREATE_RATE');
const DURATION = __ENV.MIXED_DURATION || '2m';
const RAMP_DURATION = __ENV.MIXED_RAMP_DURATION || '10s';
const WARMUP_RATE = positiveInteger(__ENV.WARMUP_RATE || '10', 'WARMUP_RATE');
const WARMUP_DURATION = __ENV.WARMUP_DURATION || '30s';
const DATA_LINKS = positiveInteger(__ENV.MIXED_DATA_LINKS || '20', 'MIXED_DATA_LINKS');
const ORIGINAL_URL_BASE = __ENV.MIXED_ORIGINAL_URL || 'https://example.com/srrrg-mixed';
const REDIRECT_PRE_ALLOCATED_VUS = positiveInteger(
  __ENV.MIXED_REDIRECT_PRE_ALLOCATED_VUS || '100',
  'MIXED_REDIRECT_PRE_ALLOCATED_VUS',
);
const REDIRECT_MAX_VUS = positiveInteger(
  __ENV.MIXED_REDIRECT_MAX_VUS || '200',
  'MIXED_REDIRECT_MAX_VUS',
);

const redirectBlocked = new Trend('mixed_redirect_client_blocked', true);
const redirectConnecting = new Trend('mixed_redirect_client_connecting', true);
const redirectTlsHandshaking = new Trend('mixed_redirect_client_tls_handshaking', true);
const redirectSending = new Trend('mixed_redirect_client_sending', true);
const redirectWaiting = new Trend('mixed_redirect_client_waiting', true);
const redirectReceiving = new Trend('mixed_redirect_client_receiving', true);

export const options = {
  setupTimeout: '2m',
  teardownTimeout: '2m',
  scenarios: {
    redirect_warmup: {
      executor: 'constant-arrival-rate',
      exec: 'redirectWarmup',
      rate: WARMUP_RATE,
      duration: WARMUP_DURATION,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 40,
    },
    redirect: {
      executor: 'ramping-arrival-rate',
      exec: 'redirect',
      startTime: WARMUP_DURATION,
      startRate: WARMUP_RATE,
      timeUnit: '1s',
      preAllocatedVUs: REDIRECT_PRE_ALLOCATED_VUS,
      maxVUs: REDIRECT_MAX_VUS,
      stages: [
        { target: REDIRECT_RATE, duration: RAMP_DURATION },
        { target: REDIRECT_RATE, duration: DURATION },
      ],
      gracefulStop: '10s',
    },
    link_create_hit: {
      executor: 'constant-arrival-rate',
      exec: 'linkCreateHit',
      startTime: `${seconds(WARMUP_DURATION) + seconds(RAMP_DURATION)}s`,
      rate: HIT_CREATE_RATE,
      duration: DURATION,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 40,
      gracefulStop: '10s',
    },
    link_create_miss: {
      executor: 'constant-arrival-rate',
      exec: 'linkCreateMiss',
      startTime: `${seconds(WARMUP_DURATION) + seconds(RAMP_DURATION)}s`,
      rate: MISS_CREATE_RATE,
      duration: DURATION,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 40,
      gracefulStop: '10s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate<0.001'],
    'http_req_duration{endpoint:mixed_redirect}': ['p(95)<100', 'p(99)<250'],
    'http_req_duration{endpoint:mixed_link_create_hit}': ['p(95)<300', 'p(99)<500'],
    'http_req_duration{endpoint:mixed_link_create_miss}': ['p(95)<300', 'p(99)<500'],
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const redirectLinks = [];
  const hitUrl = `${ORIGINAL_URL_BASE}?run=${runId}&mode=hit`;

  console.log(
      `test configuration: scenario=mixed-workload, baseUrl=${BASE_URL}, ` +
      `redirectRate=${REDIRECT_RATE}, hitCreateRate=${HIT_CREATE_RATE}, ` +
      `missCreateRate=${MISS_CREATE_RATE}, duration=${DURATION}, ` +
      `rampDuration=${RAMP_DURATION}, warmupRate=${WARMUP_RATE}, ` +
      `warmupDuration=${WARMUP_DURATION}, dataLinks=${DATA_LINKS}, ` +
      `redirectPreAllocatedVUs=${REDIRECT_PRE_ALLOCATED_VUS}, ` +
      `redirectMaxVUs=${REDIRECT_MAX_VUS}, runId=${runId}`,
  );

  for (let i = 0; i < DATA_LINKS; i += 1) {
    redirectLinks.push(createLink(
      `${ORIGINAL_URL_BASE}?run=${runId}&mode=redirect&link=${i}`,
      'mixed_setup_redirect_link',
    ));
  }
  createLink(hitUrl, 'mixed_setup_hit_seed');

  return { runId, redirectLinks, hitUrl };
}

export function redirectWarmup(data) {
  requestRedirect(data.redirectLinks, 'mixed_redirect_warmup');
}

export function redirect(data) {
  requestRedirect(data.redirectLinks, 'mixed_redirect');
}

export function linkCreateHit(data) {
  createLink(data.hitUrl, 'mixed_link_create_hit');
}

export function linkCreateMiss(data) {
  createLink(
    `${ORIGINAL_URL_BASE}?run=${data.runId}&mode=miss` +
      `&iteration=${exec.scenario.iterationInTest}&vu=${exec.vu.idInTest}`,
    'mixed_link_create_miss',
  );
}

export function teardown(data) {
  for (const link of data?.redirectLinks || []) {
    const response = http.del(`${BASE_URL}/api/links/${link.code}`, null, {
      headers: { 'X-Srrrg-Secret-Key': link.secretKey },
      tags: { endpoint: 'mixed_teardown_link_delete' },
    });
    check(response, {'mixed setup link cleanup returns 200': (res) => res.status === 200});
  }
}

function requestRedirect(links, endpoint) {
  const link = links[exec.scenario.iterationInTest % links.length];
  const response = http.get(link.shortUrl, {redirects: 0, tags: {endpoint}});
  if (endpoint === 'mixed_redirect') {
    redirectBlocked.add(response.timings.blocked);
    redirectConnecting.add(response.timings.connecting);
    redirectTlsHandshaking.add(response.timings.tls_handshaking);
    redirectSending.add(response.timings.sending);
    redirectWaiting.add(response.timings.waiting);
    redirectReceiving.add(response.timings.receiving);
  }
  check(response, {[`${endpoint} returns 302`]: (res) => res.status === 302});
}

function createLink(originalUrl, endpoint) {
  const response = http.post(
    `${BASE_URL}/api/links`,
    JSON.stringify({originalUrl, expiresAt: null}),
    {headers: {'Content-Type': 'application/json'}, tags: {endpoint}},
  );
  if (!check(response, {[`${endpoint} returns 201`]: (res) => res.status === 201})) {
    fail(`link creation failed: endpoint=${endpoint}, status=${response.status}`);
  }

  const body = response.json();
  if (!check(body, {
    [`${endpoint} has a valid code`]: (value) =>
      typeof value.code === 'string' && /^[0-9A-Za-z]{6}$/.test(value.code),
    [`${endpoint} has a secret key`]: (value) =>
      typeof value.secretKey === 'string' && value.secretKey.startsWith('srrrg_sk_'),
  })) {
    fail(`link creation response was invalid: endpoint=${endpoint}`);
  }
  return body;
}

function positiveInteger(value, name) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed < 1 || String(parsed) !== value) {
    throw new Error(`${name} must be a positive integer: ${value}`);
  }
  return parsed;
}

function seconds(value) {
  const match = /^(\d+)(s|m)$/.exec(value);
  if (!match) {
    throw new Error(`duration must use whole seconds or minutes: ${value}`);
  }
  return Number(match[1]) * (match[2] === 'm' ? 60 : 1);
}
