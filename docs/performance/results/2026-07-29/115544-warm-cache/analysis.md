# Warm-cache 350 RPS 분석

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-29 11:55:44 ~ 11:58:30 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 워밍업 | 10 RPS, 30초, 300 redirect |
| 본 측정 | 10초 ramp 후 350 RPS, 2분 유지 |
| 테스트 링크 | 20개 |
| pre-allocated / max VU | 70 / 200 |
| 애플리케이션 commit | `e7484af47a90658417d8c35b40f0a4ec28175158` |
| 인프라 commit | `40757b82baa97a336e897c12833c20697130a50b` |
| Pod | dev 1 replica, CPU 500m / memory 1Gi limit |
| URL 위험 검사 | `fixed-safe`, delay `100ms`, cache duration `15m` |
| k6 종료 코드 | 0 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| 워밍업 redirect | 300 |
| 본 측정 redirect | 43,800 |
| 전체 redirect | 44,100 |
| 전체 HTTP 요청 | 44,140 |
| check | 44,200 / 44,200 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| 본 측정 p95 | 13.18ms |
| 본 측정 p99 | 21.48ms |
| 본 측정 최대 | 83.04ms |
| 최대 활성 VU | 8 |

## Prometheus 직접 조회 결과

Counter는 테스트 직전 11:55:35와 종료 후 11:58:40 sample의 차이로 계산했고,
350 RPS 수치는 11:58:15 시점의 30초 rate를 사용했다.

| 목표 RPS | 실제 RPS | 서버 p50 | 서버 p95 | 서버 p99 |
|---:|---:|---:|---:|---:|
| 350 | 350.00 | 1.98ms | 2.64ms | 3.16ms |

| 항목 | 결과 |
|---|---:|
| click / redirect 쓰기 p95 | 1.31ms / 1.05ms |
| 애플리케이션 CPU 최대 | 443m / 500m (88.6%) |
| 애플리케이션 메모리 최대 | 486.36Mi |
| JVM heap 최대 | 106.00Mi |
| GC 평균 pause 최대 | 2.00ms |
| Tomcat busy / current 최대 관측 | 3 / 16 |
| Hikari active / pending 최대 관측 | 1 / 0 |
| Hikari timeout / Pod restart 증가 | 0 / 0 |

CPU throttled period는 전체 1,676개 중 17개로 1.01%였다. 30초 최대 비율은
7.20%, 350 RPS 안정 시점은 1.71%였다. 처리량과 지연 악화는 없지만 CPU 제한의
88.6%까지 사용했고 안정 구간에도 throttling이 발생했으므로 CPU 여유 구간의
상단에 진입한 것으로 판단한다.

## 요청 수와 캐시 검증

- k6 redirect 44,100건과 Prometheus redirect 및 cache hit 증가량이 일치했다.
- HTTP route도 302 redirect 44,100건, 링크 생성·삭제 각 20건으로 일치했다.
- `miss_stale`은 0건이고 setup에서만 `miss_absent`와 risk check가 각 20건 발생했다.
- HTTP 5xx와 redirect error는 0건이었다.

## PostgreSQL

| 항목 | 결과 |
|---|---:|
| 350 RPS commit/s | 1,404.55 |
| connection 최대 | 10 |
| 전체 commit 증가 | 176,522 |
| rollback / deadlock 증가 | 0 / 0 |
| cache hit ratio | 100% |
| temporary data 증가 | 0 B |
| CPU 최대 | 391m |
| 메모리 최대 | 212.43Mi |
| container write 최대 | 18.57MiB/s |

일반적인 access/row lock만 관측됐고 exclusive 계열 lock, deadlock, rollback,
Hikari pending과 timeout이 없어 DB 병목 징후는 없다. 상위 SQL 추가 분석은
수행하지 않았다.

## 판정

성공. 단일 dev Pod는 Warm-cache redirect 350 RPS를 2분 동안 목표 처리량 그대로
유지했고 오류, 요청 누락, 지연 변곡점, thread 및 connection 병목은 없었다.

다만 CPU 최대가 443m로 500m 제한의 88.6%이며 안정 구간 throttling도 1.71%
관측됐다. 300 RPS의 351m에서 부하와 CPU가 비슷한 비율로 증가한다고 단순
추정하면 400 RPS는 약 500m를 넘는다. 따라서 현재 자원 제한에서 350 RPS는
검증된 상한으로 기록하고 400 RPS 자동 진행은 중단한다. 400 RPS는 정상 여유
검증이 아니라 CPU 제한에서의 포화 거동을 의도적으로 확인할 때 별도 수행한다.
