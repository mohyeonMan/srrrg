# Warm-cache 단계 테스트 분석: 20·30·40 RPS

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-28 12:37:57 ~ 12:44:24 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 요청률 | 20, 30, 40 RPS |
| 각 요청률 유지 시간 | 2분 |
| 요청률 증가 시간 | 10초 |
| 테스트 링크 | 20개 |
| commit | `f7176f82acba42e7094c0f9401e9d4c2fb976d14` |
| k6 종료 코드 | 99 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| 완료 redirect iteration | 11,392 |
| dropped iteration | 7 |
| 전체 HTTP 요청 | 11,432 |
| HTTP 실패 | 0 |
| check | 11,492 / 11,492 성공 |
| warm-cache p95 | 26.55ms |
| warm-cache p99 | 88.94ms |
| 최대 지연 | 1.18초 |

p95, p99와 실패율 threshold는 통과했지만 `dropped_iterations==0` 기준을 위반해
k6 전체 판정은 실패다.

## Prometheus 단계별 결과

| 목표 RPS | 실제 RPS | 서버 p50 | 서버 p95 | 서버 p99 | CPU | 메모리 |
|---:|---:|---:|---:|---:|---:|---:|
| 20 | 20.02 | 3.42ms | 4.93ms | 6.65ms | 78m | 343.58Mi |
| 30 | 30.00 | 2.96ms | 4.04ms | 6.78ms | 76m | 346.28Mi |
| 40 | 40.00 | 2.85ms | 5.05ms | 26.48ms | 72m | 346.81Mi |

| 목표 RPS | click 쓰기 p95 | redirect 쓰기 p95 | Hikari pending | Tomcat busy 최대 | PostgreSQL commit/s |
|---:|---:|---:|---:|---:|---:|
| 20 | 2.27ms | 1.43ms | 0 | 2 | 79.60 |
| 30 | 2.06ms | 1.20ms | 0 | 1 | 120.68 |
| 40 | 2.67ms | 1.30ms | 0 | 4 | 160.34 |

모든 단계에서 CPU throttling, HTTP 5xx, Hikari timeout, PostgreSQL deadlock과
Pod restart는 0이었다. PostgreSQL connection은 10개로 유지됐다.

## Dropped iteration 분석

진행 로그에서 30 RPS ramp 직후 약 1초 동안 활성 VU가 25개까지 증가했다. 당시
pre-allocated VU는 최대 RPS의 절반인 20개였고 k6가 VU를 동적으로 늘리는 동안
7개 iteration을 시작하지 못했다.

k6 최대 지연은 1.18초였지만 같은 실행 구간의 서버 histogram에서는 250ms를 넘은
redirect가 0건이었다. 문제 시각에도 서버 p99 8.08ms, Hikari pending 0, Tomcat busy 1,
DB lock과 deadlock 0이었다. 따라서 지속적인 서버 포화보다 load generator PC 또는
네트워크의 단발성 지연과 부족한 pre-allocation이 원인일 가능성이 높다.

재발 방지를 위해 기본 pre-allocated VU를 최대 RPS의 2배, max VU를 4배로 변경했다.

## Cache TTL 영향

setup 시각부터 약 5분 후 테스트 링크 20개의 cache가 만료됐다. 40 RPS 후반에
`miss_stale`과 fixed-safe risk check가 각각 20회 발생했다.

- 완료 redirect: 11,392건
- cache hit: 11,372건
- stale miss 및 risk check: 20건

전체 요청의 약 0.18%라 p95에는 큰 영향을 주지 않았지만, 40 RPS 후반은 엄밀한
순수 warm-cache 구간이 아니다. 다음 장시간 warm-cache 테스트에서는 fixed-safe
cache duration을 전체 실행 시간보다 길게 설정해야 한다.

## 판정

서버 성능은 40 RPS까지 통과했다. 실제 RPS를 유지했고 서버 p95 5.05ms, p99 26.48ms,
오류와 포화 지표가 모두 0이었다.

그러나 테스트 실행 품질은 dropped iteration 7건과 cache TTL 만료 때문에 실패로
판정한다. 최대 지속 처리량을 확정하는 근거로 사용하지 않고, 아래 조건으로 40 RPS를
재검증한다.

1. fixed-safe cache duration을 15분 이상으로 설정한다.
2. pre-allocated VU 80, max VU 160을 사용한다.
3. 40 RPS를 최소 3분 유지한다.
4. dropped iteration 0과 `miss_stale` 0을 확인한다.
