# Warm-cache 50·60·80 RPS 단계 테스트 분석

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-28 23:53:29 ~ 2026-07-29 00:00:26 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 워밍업 | 10 RPS, 30초 |
| 본 측정 | 50·60·80 RPS, 각 2분 |
| 요청률 증가 시간 | 각 10초 |
| 테스트 링크 | 20개 |
| pre-allocated / max VU | 본 측정 160 / 320 |
| 애플리케이션 commit | `1f43e7453ff031c7b58ead0e6305f6ed58f40097` |
| 스크립트 상태 | commit 이후 워밍업 시나리오가 추가된 작업 트리 |
| URL 위험 검사 | `fixed-safe`, delay `100ms`, cache duration `15m` |
| k6 종료 코드 | 0 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| 워밍업 redirect | 300 |
| 본 측정 redirect | 24,049 |
| 전체 HTTP 요청 | 24,389 |
| check | 24,449 / 24,449 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| 본 측정 평균 | 11.12ms |
| 본 측정 중앙값 | 9.08ms |
| 본 측정 p95 | 22.07ms |
| 본 측정 p99 | 23.65ms |
| 본 측정 최대 | 61.56ms |

p95 100ms, p99 250ms, HTTP 실패율 0.1% 미만과 dropped iteration 0 기준을
모두 통과했다.

## Prometheus 단계별 결과

| 목표 RPS | 실제 RPS | 서버 p50 | 서버 p95 | 서버 p99 | CPU | 메모리 최대 |
|---:|---:|---:|---:|---:|---:|---:|
| 50 | 50.00 | 2.71ms | 3.41ms | 4.02ms | 66m | 364.93Mi |
| 60 | 59.98 | 2.49ms | 3.14ms | 3.71ms | 62m | 365.06Mi |
| 80 | 80.00 | 2.35ms | 3.01ms | 3.54ms | 84m | 365.09Mi |

| 목표 RPS | click 쓰기 p95 | redirect 쓰기 p95 | Hikari active 최대 관측 | pending | Tomcat busy 최대 관측 | PostgreSQL commit/s |
|---:|---:|---:|---:|---:|---:|---:|
| 50 | 1.94ms | 0.99ms | 1 | 0 | 2 | 202.10 |
| 60 | 1.74ms | 0.99ms | 1 | 0 | 2 | 240.40 |
| 80 | 1.66ms | 0.99ms | 1 | 0 | 2 | 323.36 |

모든 단계에서 서버 redirect 250ms 초과 요청은 0이었다. 전체 실행 중 Hikari
timeout, HTTP 5xx, PostgreSQL deadlock, Pod restart와 CPU throttled period도
발생하지 않았다. PostgreSQL connection은 10개로 유지됐다.

HikariCP와 Tomcat gauge는 5초 scrape 사이에 끝난 짧은 동작의 순간 최고치를 놓칠 수
있다. 단계별 CPU 값은 각 단계 종료 시점의 1분 rate이며 절대 최대값은 아니다.

## 캐시 및 요청 수 검증

- 워밍업과 본 측정을 합친 k6 redirect는 24,349건이다.
- Prometheus의 redirect와 cache hit도 각각 정확히 24,349건 증가했다.
- `miss_stale`은 증가하지 않았다.
- setup에서 링크 20개를 생성하며 risk check가 20회 발생했고, 워밍업과 본 측정
  구간에는 추가 risk check가 없었다.
- teardown에서 테스트 링크 20개를 모두 삭제했다.

## 해설

- 50, 60, 80 RPS에서 실제 처리량이 목표값과 일치했고 dropped iteration이 없었다.
- 부하 증가에도 서버 p95와 p99가 증가하지 않았다. 지속 포화나 latency 변곡점은
  아직 나타나지 않았다.
- 80 RPS의 애플리케이션 CPU는 약 84m로 500m limit의 약 17%이며 throttling이 없다.
  메모리도 모든 단계에서 약 365Mi로 안정적이다.
- Hikari pending과 timeout이 없고 쓰기 p95가 약 1~2ms로 유지돼 현재 구간에서는
  DB connection pool과 쓰기 트랜잭션이 병목이 아니다.
- PostgreSQL commit/s는 요청률에 비례해 증가했지만 connection, deadlock과 지연
  악화가 관측되지 않았다.

## 판정

성공. Warm-cache 정상 상태에서 단일 dev Pod는 80 RPS까지 오류, dropped iteration,
stale miss와 포화 징후 없이 처리했다. 현재 테스트에서는 최대 지속 처리량을 찾지
못했다.

다음 테스트는 동일한 워밍업과 2분 유지 조건에서 100·150·200 RPS를 실행해 CPU,
Hikari pending, Tomcat busy thread와 서버 지연의 최초 변곡점을 확인한다.
