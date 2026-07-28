# 워밍업 후 Warm-cache 40 RPS 재검증 분석

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-28 23:46:06 ~ 23:49:42 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 워밍업 | 10 RPS, 30초 |
| 본 측정 | 40 RPS, 3분 |
| 테스트 링크 | 20개 |
| pre-allocated / max VU | 본 측정 80 / 160 |
| 애플리케이션 commit | `1f43e7453ff031c7b58ead0e6305f6ed58f40097` |
| 스크립트 상태 | commit 이후 워밍업 시나리오가 추가된 작업 트리 |
| URL 위험 검사 | `fixed-safe`, delay `100ms`, cache duration `15m` |
| k6 종료 코드 | 0 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| 워밍업 redirect | 300 |
| 본 측정 redirect | 7,199 |
| 전체 HTTP 요청 | 7,539 |
| check | 7,599 / 7,599 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| 본 측정 평균 | 12.95ms |
| 본 측정 중앙값 | 10.09ms |
| 본 측정 p95 | 27.58ms |
| 본 측정 p99 | 28.73ms |
| 본 측정 최대 | 75.21ms |

p95 100ms, p99 250ms, HTTP 실패율 0.1% 미만과 dropped iteration 0 기준을
모두 통과했다.

## Prometheus 본 측정 구간

| 구간 | 실제 RPS | 서버 p50 | 서버 p95 | 서버 p99 | 애플리케이션 CPU |
|---|---:|---:|---:|---:|---:|
| 첫 1분 | 40 | 2.91ms | 3.74ms | 5.42ms | 65m |
| 중간 1분 | 40 | 2.84ms | 3.52ms | 5.20ms | 56m |
| 마지막 1분 | 40 | 2.87ms | 3.68ms | 5.34ms | 55m |

| 항목 | 결과 |
|---|---:|
| 본 측정 중 250ms 초과 서버 redirect | 0 |
| click 쓰기 마지막 구간 p95 | 2.03ms |
| redirect 쓰기 마지막 구간 p95 | 1.12ms |
| Hikari pending 최대 관측 / timeout 증가 | 0 / 0 |
| Tomcat busy thread 최대 관측 | 1 |
| 애플리케이션 메모리 최대 | 364.45Mi |
| 마지막 구간 PostgreSQL commit/s | 158.52 |
| PostgreSQL connection | 10 |
| HTTP 5xx / deadlock / Pod restart | 모두 0 |

HikariCP와 Tomcat gauge는 5초 scrape 사이에 끝난 짧은 동작의 순간 최고치를 놓칠 수
있다. Timer, Counter와 지속적인 pending 또는 timeout 여부를 함께 사용해 판정했다.

## 캐시 및 요청 수 검증

- 워밍업과 본 측정을 합친 k6 redirect는 7,499건이다.
- Prometheus의 redirect와 cache hit도 각각 정확히 7,499건 증가했다.
- `miss_stale`은 증가하지 않았다.
- setup에서 링크 20개를 생성하며 risk check가 20회 발생했고, 워밍업과 본 측정
  구간에는 추가 risk check가 없었다.
- teardown에서 테스트 링크 20개를 모두 삭제했다.

## 비교 및 해설

워밍업 없이 같은 40 RPS를 실행한 직전 결과는 시작 후 약 4초 동안 지연이 집중돼
k6 p99 344.11ms, 서버 첫 구간 p99 501.45ms를 기록했다. 이번 실행은 10 RPS로
30초 워밍업한 뒤 본 측정을 시작했고, 본 측정 첫 1분부터 서버 p99 5.42ms로 안정됐다.

따라서 직전 p99 실패는 40 RPS의 지속 처리 한계보다 애플리케이션과 요청 경로가
충분히 준비되지 않은 상태에서 부하를 바로 시작한 영향으로 판단한다. 워밍업 없는
초기 지연 결과는 향후 cold-start 또는 spike 테스트의 기준으로 유지한다.

## 판정

성공. Warm-cache 정상 상태에서 단일 dev Pod는 40 RPS를 3분 동안 오류, dropped
iteration, stale miss와 포화 징후 없이 처리했다. 클라이언트 p95와 p99 및 서버 지연
기준을 모두 만족했다.

다음 테스트는 동일한 워밍업 조건에서 50·60·80 RPS를 단계별로 실행해 최초 지연
변곡점과 자원 포화 지점을 찾는다.
