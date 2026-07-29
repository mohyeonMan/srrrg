# Warm-cache 275 RPS 테스트 분석

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-29 11:38:02 ~ 11:40:52 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 워밍업 | 10 RPS, 30초, 300 redirect |
| 본 측정 | 10초 ramp 후 275 RPS, 2분 유지 |
| 테스트 링크 | 20개 |
| pre-allocated / max VU | 55 / 200 |
| 애플리케이션 commit | `e7484af47a90658417d8c35b40f0a4ec28175158` |
| 인프라 commit | `40757b82baa97a336e897c12833c20697130a50b` |
| Pod | dev 1 replica, CPU 500m / memory 1Gi limit |
| URL 위험 검사 | `fixed-safe`, delay `100ms`, cache duration `15m` |
| k6 종료 코드 | 0 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| 워밍업 redirect | 300 |
| 본 측정 redirect | 34,425 |
| 전체 redirect | 34,725 |
| 전체 HTTP 요청 | 34,765 |
| check | 34,825 / 34,825 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| 본 측정 p95 | 12.84ms |
| 본 측정 p99 | 20.02ms |
| 본 측정 최대 | 59.87ms |
| 최대 활성 VU | 9 |

모든 k6 threshold를 통과했고 테스트 링크 20개도 전부 정리됐다.

## Prometheus 직접 조회 결과

Counter는 테스트 직전 11:37:55와 종료 후 11:41:00 sample의 차이로 계산했고,
275 RPS 수치는 11:40:40 시점의 30초 rate를 사용했다.

| 목표 RPS | 실제 RPS | 서버 p50 | 서버 p95 | 서버 p99 |
|---:|---:|---:|---:|---:|
| 275 | 275.00 | 1.98ms | 2.44ms | 2.79ms |

| 항목 | 결과 |
|---|---:|
| click / redirect 쓰기 p95 | 1.19ms / 0.99ms |
| 애플리케이션 CPU 최대 | 321m |
| 애플리케이션 메모리 최대 | 485.85Mi |
| JVM heap 최대 | 151.16Mi |
| GC 평균 pause 최대 | 1.50ms |
| Tomcat busy 최대 관측 | 3 |
| Hikari active / pending 최대 관측 | 2 / 0 |
| Hikari timeout / Pod restart 증가 | 0 / 0 |

CPU throttled period는 전체 1,584개 중 11개로 0.69%였다. 30초 최대 비율은
8.46%였지만 275 RPS 안정 시점은 0%였고 처리량과 지연 악화는 없었다.

## 요청 수와 캐시 검증

- k6 redirect 34,725건과 Prometheus redirect 및 cache hit 증가량이 일치했다.
- HTTP route도 302 redirect 34,725건, 링크 생성·삭제 각 20건으로 일치했다.
- `miss_stale`은 0건이고 setup에서만 `miss_absent`와 risk check가 각 20건 발생했다.
- HTTP 5xx와 redirect error는 0건이었다.

## PostgreSQL

| 항목 | 결과 |
|---|---:|
| 275 RPS commit/s | 1,103.90 |
| connection 최대 | 10 |
| 전체 commit 증가 | 139,283 |
| rollback / deadlock 증가 | 0 / 0 |
| cache hit ratio | 100% |
| temporary data 증가 | 0 B |
| CPU 최대 | 306m |
| 메모리 최대 | 188.18Mi |
| container read / write 최대 | 0 / 15.67MiB/s |

일반적인 `AccessShareLock`, `RowExclusiveLock`, `RowShareLock`만 관측됐고 exclusive
계열 lock, deadlock, rollback, Hikari pending과 timeout이 없어 DB 병목 징후는
없다. 따라서 상위 SQL 추가 분석은 수행하지 않았다.

## 판정

성공. 단일 dev Pod는 Warm-cache redirect 275 RPS를 2분 동안 목표 처리량 그대로
유지했다. 오류·드롭·restart·connection 대기 없이 서버 p95 2.44ms와 p99
2.79ms로 안정적이었다.

다음 테스트는 보정된 10초 ramp와 최대 200 VU 조건을 유지해 300 RPS를 단독으로
2분 검증한다. 이전 300 RPS 실패는 즉시 점프와 최대 1,200 VU 조건에서 발생했으므로
이번 결과와 직접 비교하지 않는다.
