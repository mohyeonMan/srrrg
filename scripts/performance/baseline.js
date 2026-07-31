import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'https://jhhomehub.gonetis.com/srrrg-dev').replace(/\/+$/, '');
const DURATION = __ENV.BASELINE_DURATION || '1m';
const ORIGINAL_URL_BASE = __ENV.BASELINE_ORIGINAL_URL || 'https://example.com/srrrg-baseline';
const linkCreateDuration = new Trend('baseline_link_create_duration', true);
const managementGetDuration = new Trend('baseline_management_get_duration', true);
const redirectDuration = new Trend('baseline_redirect_duration', true);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    baseline: {
      executor: 'constant-vus',
      vus: 1,
      duration: DURATION,
      gracefulStop: '10s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
  },
};

export function setup() {
  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const originalUrl = `${ORIGINAL_URL_BASE}?run=${runId}`;
  console.log(`test configuration: scenario=baseline, baseUrl=${BASE_URL}, duration=${DURATION}`);

  return {
    originalUrl,
    seed: createLink(originalUrl, 'baseline_setup_seed'),
  };
}

export default function (data) {
  const link = createLink(data.originalUrl, 'baseline_link_create');

  try {
    const managementResponse = http.get(`${BASE_URL}/api/links/${link.code}`, {
      headers: { 'X-Srrrg-Secret-Key': link.secretKey },
      tags: { endpoint: 'baseline_management_get' },
    });
    managementGetDuration.add(managementResponse.timings.duration);
    check(managementResponse, {
      'baseline management lookup returns 200': (res) => res.status === 200,
      'baseline management lookup returns the original URL': (res) => {
        try {
          return res.json('originalUrl') === data.originalUrl;
        } catch (_) {
          return false;
        }
      },
    });

    const redirectResponse = http.get(link.shortUrl, {
      redirects: 0,
      tags: { endpoint: 'baseline_redirect' },
    });
    redirectDuration.add(redirectResponse.timings.duration);
    check(redirectResponse, {
      'baseline redirect returns 302': (res) => res.status === 302,
      'baseline redirect location matches the original URL': (res) =>
        res.headers.Location === data.originalUrl,
    });
  } finally {
    deleteLink(link, 'baseline_link_delete');
  }
}

export function teardown(data) {
  if (data?.seed) {
    deleteLink(data.seed, 'baseline_setup_delete');
  }
}

function createLink(originalUrl, endpoint) {
  const response = http.post(
    `${BASE_URL}/api/links`,
    JSON.stringify({ originalUrl, expiresAt: null }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint },
    },
  );
  if (endpoint === 'baseline_link_create') {
    linkCreateDuration.add(response.timings.duration);
  }

  if (!check(response, { [`${endpoint} returns 201`]: (res) => res.status === 201 })) {
    fail(`link creation failed: endpoint=${endpoint}, status=${response.status}`);
  }

  const body = response.json();
  if (!check(body, {
    [`${endpoint} response is valid`]: (value) =>
      typeof value.code === 'string' &&
      typeof value.shortUrl === 'string' &&
      typeof value.secretKey === 'string',
  })) {
    fail(`link creation response was invalid: endpoint=${endpoint}`);
  }
  return body;
}

function deleteLink(link, endpoint) {
  const response = http.del(`${BASE_URL}/api/links/${link.code}`, null, {
    headers: { 'X-Srrrg-Secret-Key': link.secretKey },
    tags: { endpoint },
  });
  check(response, { [`${endpoint} returns 200`]: (res) => res.status === 200 });
}
