import http from 'k6/http';
import { check, fail } from 'k6';

// 환경 변수로 대상 서버와 반복 횟수를 바꿀 수 있음.
const BASE_URL = (__ENV.BASE_URL || 'https://jhhomehub.gonetis.com/srrrg-dev').replace(/\/+$/, '');
const REDIRECT_REQUESTS = parsePositiveInteger(__ENV.SMOKE_REDIRECT_REQUESTS || '20');
const ORIGINAL_URL_BASE = __ENV.SMOKE_ORIGINAL_URL || 'https://example.com/srrrg-smoke';

// 부하 측정이 아니라 메트릭 연결 확인이 목적이므로 VU 1에서 한 번만 실행함.
export const options = {
  scenarios: {
    smoke: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '1m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
  },
};

export function setup() {
  console.log(
    `test configuration: scenario=smoke, baseUrl=${BASE_URL}, redirectRequests=${REDIRECT_REQUESTS}`,
  );

  // 실행마다 다른 URL을 사용해 링크 생성 시 cache miss와 risk check를 발생시킴.
  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const separator = ORIGINAL_URL_BASE.includes('?') ? '&' : '?';
  const originalUrl = `${ORIGINAL_URL_BASE}${separator}srrrg_smoke_run=${runId}`;
  const response = http.post(
    `${BASE_URL}/api/links`,
    JSON.stringify({ originalUrl, expiresAt: null }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'link_create' },
    },
  );

  const created = check(response, {
    'link create returns 201': (res) => res.status === 201,
  });
  if (!created) {
    fail(`link creation failed: status=${response.status}, body=${response.body}`);
  }

  let body;
  try {
    body = response.json();
  } catch (error) {
    fail(`link creation returned invalid JSON: ${error.message}`);
  }

  const validResponse = check(body, {
    'link create response has a six-character code': (value) =>
      typeof value.code === 'string' && /^[0-9A-Za-z]{6}$/.test(value.code),
    'link create response has a short URL': (value) =>
      typeof value.shortUrl === 'string' && value.shortUrl.length > 0,
    'link create response has a secret key': (value) =>
      typeof value.secretKey === 'string' && value.secretKey.startsWith('srrrg_sk_'),
  });
  if (!validResponse) {
    fail('link creation response did not match the expected contract');
  }

  console.log(
    `test link created: code=${body.code}, shortUrl=${body.shortUrl}, secretKey=${body.secretKey}`,
  );

  return {
    code: body.code,
    shortUrl: body.shortUrl,
    secretKey: body.secretKey,
    originalUrl,
  };
}

export default function (data) {
  // 생성 응답의 secret key로 관리 API 계약을 확인.
  const managementResponse = http.get(`${BASE_URL}/api/links/${data.code}`, {
    headers: { 'X-Srrrg-Secret-Key': data.secretKey },
    tags: { endpoint: 'link_management_get' },
  });
  check(managementResponse, {
    'management lookup returns 200': (res) => res.status === 200,
    'management lookup returns the original URL': (res) => {
      try {
        return res.json('originalUrl') === data.originalUrl;
      } catch (_) {
        return false;
      }
    },
  });

  // redirect를 따라가지 않고 애플리케이션이 반환한 302와 Location만 확인.
  for (let i = 0; i < REDIRECT_REQUESTS; i += 1) {
    const redirectResponse = http.get(data.shortUrl, {
      redirects: 0,
      tags: { endpoint: 'redirect_warm_cache' },
    });

    check(redirectResponse, {
      'redirect returns 302': (res) => res.status === 302,
      'redirect location matches the original URL': (res) =>
        res.headers.Location === data.originalUrl,
    });
  }
}

export function teardown(data) {
  // 생성한 테스트 링크를 secret key로 인증해 정리함.
  if (!data || !data.code || !data.secretKey) {
    return;
  }

  const response = http.del(`${BASE_URL}/api/links/${data.code}`, null, {
    headers: { 'X-Srrrg-Secret-Key': data.secretKey },
    tags: { endpoint: 'link_delete' },
  });

  check(response, {
    'test link cleanup returns 200': (res) => res.status === 200,
    'test link cleanup confirms deletion': (res) => {
      try {
        return res.json('deleted') === true;
      } catch (_) {
        return false;
      }
    },
  });
}

function parsePositiveInteger(value) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new Error(`SMOKE_REDIRECT_REQUESTS must be a positive integer: ${value}`);
  }
  return parsed;
}
