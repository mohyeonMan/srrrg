# Warm-cache calibration 분석: 5·10·20 RPS

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-28 12:20:18 ~ 12:22:05 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 요청률 | 5, 10, 20 RPS |
| 각 요청률 유지 시간 | 30초 |
| 요청률 증가 시간 | 5초 |
| 테스트 링크 | 20개 |
| 애플리케이션 commit | `f7176f82acba42e7094c0f9401e9d4c2fb976d14` |
| 작업 트리 | warm-cache 테스트 파일 추가 상태 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| redirect iteration | 1,162 |
| 전체 HTTP 요청 | 1,202 |
| check | 1,262 / 1,262 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| warm-cache 평균 | 16.35ms |
| warm-cache 중앙값 | 14.59ms |
| warm-cache p95 | 25.31ms |
| warm-cache p99 | 50.21ms |
| warm-cache 최대 | 127.06ms |

목표 요청 수는 유지 구간과 ramp 구간을 합산하면 약 1,162건이며 실제 iteration과
일치한다. k6 threshold는 모두 통과했고 종료 코드는 0이다.

## Prometheus 단계별 결과

| 목표 RPS | 실제 RPS | redirect p50 | redirect p95 | redirect p99 | click 쓰기 p95 | redirect 쓰기 p95 |
|---:|---:|---:|---:|---:|---:|---:|
| 5 | 5 | 8.68ms | 10.07ms | 10.96ms | 4.82ms | 3.07ms |
| 10 | 10 | 5.01ms | 7.25ms | 8.30ms | 3.24ms | 2.27ms |
| 20 | 20 | 3.91ms | 5.72ms | 9.79ms | 2.45ms | 1.73ms |

| 목표 RPS | Hikari pending | Tomcat busy 최대 관측 | PostgreSQL connection | commit/s | HTTP 5xx |
|---:|---:|---:|---:|---:|---:|
| 5 | 0 | 1 | 10 | 18.5 | 0 |
| 10 | 0 | 2 | 10 | 40.8 | 0 |
| 20 | 0 | 1 | 10 | 74.9 | 0 |

20 RPS 시점의 단일 dev Pod CPU는 약 292m, 메모리는 약 330Mi였다. PostgreSQL에는
`RowExclusiveLock` 3개와 `RowShareLock` 1개가 관측됐지만 deadlock은 없었다.

## 해설

- 실제 RPS가 모든 단계에서 목표값과 정확히 일치했고 dropped iteration이 없어
  애플리케이션이 20 RPS까지 여유 있게 처리했다.
- 부하가 증가할수록 지연이 감소한 것은 포화가 아니라 JVM, connection과 PostgreSQL
  cache가 워밍된 영향으로 판단한다.
- HikariCP pending과 timeout, HTTP 5xx, Pod restart가 없어 connection pool과
  애플리케이션 안정성 문제는 관측되지 않았다.
- PostgreSQL commit 처리량이 요청률과 함께 증가했다. redirect 한 건이 click과
  redirect 기록을 별도 트랜잭션으로 처리하므로 요청률보다 높은 commit/s는 예상된 결과다.
- 20 RPS CPU 292m는 limit 500m의 약 58%다. 단일 시점 관측이므로 선형 외삽은
  금지하지만, 다음 단계에서 CPU throttling을 주의 깊게 볼 필요가 있다.
- 컨테이너와 PostgreSQL 일부 메트릭의 scrape 간격에 비해 30초 단계는 짧다.
  resource 추세를 안정적으로 비교하려면 다음 실행부터 각 단계를 최소 2분 유지한다.

## 판정

성공. 20 RPS까지 초기 p95 100ms, p99 250ms 기준을 큰 폭으로 만족했다.
현재 구간에서는 처리량 한계를 찾지 못했다.

다음 실행은 20, 30, 40 RPS를 각 2분 유지해 CPU limit 접근, throttling, Hikari pending과
지연 변곡점을 확인한다. 30 RPS 이전에 CPU 또는 p95가 급증하면 즉시 상위 단계를 중단한다.
