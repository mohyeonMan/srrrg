# Warm-cache 40 RPS 재검증 분석

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-28 23:28:34 ~ 23:31:42 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 부하 | 40 RPS, 3분 |
| 테스트 링크 | 20개 |
| pre-allocated / max VU | 80 / 160 |
| 애플리케이션 commit | `1f43e7453ff031c7b58ead0e6305f6ed58f40097` |
| URL 위험 검사 | `fixed-safe`, delay `100ms`, cache duration `15m` |
| HikariCP | maximum 10, minimum idle 10, connection timeout 2초, validation timeout 1초 |
| k6 종료 코드 | 99 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| redirect iteration | 7,199 |
| 전체 HTTP 요청 | 7,239 |
| check | 7,299 / 7,299 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| warm-cache 평균 | 25.70ms |
| warm-cache 중앙값 | 14.44ms |
| warm-cache p95 | 47.59ms |
| warm-cache p99 | 344.11ms |
| warm-cache 최대 | 1.07초 |

요청 실패율, p95와 dropped iteration threshold는 통과했다. p99 250ms 기준을
초과해 k6 전체 판정은 실패다.

## Prometheus 구간별 결과

| 구간 | 실제 RPS | 서버 p95 | 서버 p99 | 애플리케이션 CPU |
|---|---:|---:|---:|---:|
| 첫 1분 | 39.61 | 143.23ms | 501.45ms | 270m |
| 중간 1분 | 40.00 | 4.85ms | 33.55ms | 128m |
| 마지막 1분 | 40.00 | 3.71ms | 5.55ms | 73m |

| 항목 | 결과 |
|---|---:|
| 250ms 초과 서버 redirect | 약 82건 |
| click 쓰기 마지막 구간 p95 | 2.02ms |
| redirect 쓰기 마지막 구간 p95 | 1.11ms |
| Hikari active 최대 / pending 최대 | 4 / 0 |
| Hikari timeout 증가 | 0 |
| Tomcat busy thread 최대 | 8 |
| 애플리케이션 메모리 최대 | 381.06Mi |
| 마지막 구간 PostgreSQL commit/s | 165.06 |
| PostgreSQL connection | 10 |
| CPU throttling / HTTP 5xx / deadlock / Pod restart | 모두 0 |

## 캐시 및 요청 수 검증

- 완료된 k6 redirect와 Prometheus redirect 증가는 각각 7,199건으로 일치했다.
- cache hit도 7,199건 증가해 모든 redirect가 warm-cache 조건으로 실행됐다.
- 부하 구간의 `miss_stale`과 URL 위험 검사 추가 호출은 0건이었다.
- setup에서 생성한 20개 링크는 teardown에서 모두 삭제됐다.

## 해설

- 이전 실행에서 발생한 dropped iteration은 pre-allocated VU를 80으로 늘린 뒤
  재발하지 않았다.
- cache duration 15분이 적용돼 3분 부하 동안 stale miss가 발생하지 않았다.
- 250ms 이상 느린 k6 요청 97건은 모두 부하 시작 후 약 4초 안에 발생했다.
  Prometheus에서도 서버 redirect 250ms 초과가 약 82건 관측돼 클라이언트 네트워크만의
  지연은 아니다.
- 첫 구간 이후 실제 RPS는 40으로 유지됐고 지연, CPU, HikariCP와 Tomcat 지표가 빠르게
  안정됐다. 따라서 40 RPS의 지속 포화나 DB connection 병목으로 보이지 않는다.
- 현재 시나리오는 setup 직후 40 RPS를 바로 시작한다. 다음 실행에서는 낮은 요청률의
  준비 구간을 threshold 평가에서 분리하거나 10→20→40 RPS로 증가시켜 초기 지연과
  정상 상태 처리량을 별도로 판정해야 한다.

## 판정

테스트 데이터 품질은 개선됐다. dropped iteration 0, stale miss 0과 요청 수 일치를
확인했다. 그러나 전체 실행 p99가 250ms 기준을 초과했으므로 성능 판정은 실패다.

40 RPS의 정상 상태 처리 능력은 확인했지만 최대 지속 처리량을 확정하지 않는다.
다음 테스트에서는 초기 준비 구간을 명시적으로 분리한 뒤 같은 40 RPS 조건을 다시
실행하고, 통과하면 50·60·80 RPS 단계 테스트로 진행한다.
