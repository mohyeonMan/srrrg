import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'https://jhhomehub.gonetis.com/srrrg-dev').replace(/\/+$/, '');
const CONCURRENT_VUS = parsePositiveInteger(__ENV.CONCURRENT_VUS || '10', 'CONCURRENT_VUS');
const WARMUP_RATE = parsePositiveInteger(__ENV.WARMUP_RATE || '10', 'WARMUP_RATE');
const WARMUP_DURATION = __ENV.WARMUP_DURATION || '30s';
const CONCURRENT_START_TIME = __ENV.CONCURRENT_START_TIME || '45s';
const ORIGINAL_URL_BASE =
  __ENV.CONCURRENT_ORIGINAL_URL || 'https://example.com/srrrg-concurrent-cold-cache';

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
    concurrent_cold_cache: {
      executor: 'per-vu-iterations',
      exec: 'concurrentColdCache',
      startTime: CONCURRENT_START_TIME,
      vus: CONCURRENT_VUS,
      iterations: 1,
      maxDuration: '30s',
      gracefulStop: '5s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate<0.001'],
    'http_req_duration{endpoint:redirect_concurrent_cold_cache}': [
      'p(95)<500',
      'p(99)<750',
    ],
  },
};

export function setup() {
  console.log(
      `test configuration: scenario=concurrent-cold-cache, baseUrl=${BASE_URL}, ` +
      `concurrentVUs=${CONCURRENT_VUS}, warmupRate=${WARMUP_RATE}, ` +
      `warmupDuration=${WARMUP_DURATION}, concurrentStartTime=${CONCURRENT_START_TIME}`,
  );

  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const warmupLink = createLink(`${ORIGINAL_URL_BASE}?run=${runId}&purpose=warmup`);
  const targetLink = createLink(`${ORIGINAL_URL_BASE}?run=${runId}&purpose=target`);
  return { links: [warmupLink, targetLink], warmupLink, targetLink };
}

export function warmup(data) {
  requestRedirect(data.warmupLink, 'redirect_concurrent_warmup');
}

export function concurrentColdCache(data) {
  requestRedirect(data.targetLink, 'redirect_concurrent_cold_cache');
}

export function teardown(data) {
  cleanupLinks(data?.links || []);
}

function createLink(originalUrl) {
  const response = http.post(
    `${BASE_URL}/api/links`,
    JSON.stringify({ originalUrl, expiresAt: null }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'link_create_setup' },
    },
  );

  if (!check(response, { 'setup link create returns 201': (res) => res.status === 201 })) {
    fail(`setup link creation failed: status=${response.status}, body=${response.body}`);
  }

  const body = response.json();
  if (!check(body, {
    'setup link has a valid code': (value) =>
      typeof value.code === 'string' && /^[0-9A-Za-z]{6}$/.test(value.code),
    'setup link has a secret key': (value) =>
      typeof value.secretKey === 'string' && value.secretKey.startsWith('srrrg_sk_'),
  })) {
    fail('setup link response was invalid');
  }

  return {
    code: body.code,
    shortUrl: body.shortUrl,
    secretKey: body.secretKey,
  };
}

function requestRedirect(link, endpoint) {
  const response = http.get(link.shortUrl, {
    redirects: 0,
    tags: { endpoint },
  });
  check(response, {
    [`${endpoint} returns 302`]: (res) => res.status === 302,
  });
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

function parsePositiveInteger(value, name) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed < 1 || String(parsed) !== value) {
    throw new Error(`${name} must be a positive integer: ${value}`);
  }
  return parsed;
}
