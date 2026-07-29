# Warm-cache 225·250 RPS 단계 테스트 분석

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-29 11:28:35 ~ 11:33:31 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 워밍업 | 10 RPS, 30초, 300 redirect |
| 본 측정 | 225·250 RPS, 각 2분 유지 |
| 요청률 증가 시간 | 각 10초 |
| 테스트 링크 | 20개 |
| pre-allocated / max VU | 50 / 200 |
| 애플리케이션 commit | `e7484af47a90658417d8c35b40f0a4ec28175158` |
| 인프라 commit | `40757b82baa97a336e897c12833c20697130a50b` |
| Pod | dev 1 replica, CPU 500m / memory 1Gi limit |
| URL 위험 검사 | `fixed-safe`, delay `100ms`, cache duration `15m` |
| k6 종료 코드 | 0 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| 워밍업 redirect | 300 |
| 본 측정 redirect | 60,551 |
| 전체 redirect | 60,851 |
| 전체 HTTP 요청 | 60,891 |
| check | 60,951 / 60,951 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| 본 측정 p95 | 14.01ms |
| 본 측정 p99 | 23.99ms |
| 본 측정 최대 | 270.84ms |
| 최대 활성 VU | 7 |

모든 k6 threshold를 통과했고 테스트 링크 20개도 전부 정리됐다.

## Prometheus 직접 조회 결과

결과는 클러스터 내부 Prometheus HTTP API를 직접 조회했다. Counter는 테스트 직전
11:28:30과 종료 후 11:33:40 sample의 차이로 계산했고, 각 단계 수치는 유지 구간
후반의 30초 rate 및 2분 resource subquery를 사용했다.

| 목표 RPS | 실제 RPS | 서버 p50 | 서버 p95 | 서버 p99 |
|---:|---:|---:|---:|---:|
| 225 | 225.00 | 2.07ms | 2.49ms | 3.05ms |
| 250 | 250.00 | 2.04ms | 2.59ms | 3.23ms |

| 목표 RPS | click 쓰기 p95 | redirect 쓰기 p95 | CPU 최대 | 메모리 최대 |
|---:|---:|---:|---:|---:|
| 225 | 1.27ms | 0.99ms | 308m | 484.87Mi |
| 250 | 1.28ms | 1.00ms | 297m | 485.22Mi |

| 목표 RPS | throttling 2분 최대 | 안정 시점 | Hikari active / pending 최대 | Tomcat busy 최대 |
|---:|---:|---:|---:|---:|
| 225 | 0.95% | 0% | 3 / 0 | 4 |
| 250 | 2.65% | 0% | 1 / 0 | 2 |

전체 실행에서 CPU throttled period는 2,893개 중 16개로 0.55%였다. 짧은 구간의
30초 최대 비율은 7.83%였지만 각 유지 구간 후반은 모두 0%였고 처리량과 지연이
악화되지 않았다. Pod restart와 Hikari timeout 증가는 0이었다.

## 요청 수와 캐시 검증

- k6 redirect 60,851건과 Prometheus redirect 및 cache hit 증가량이 일치했다.
- HTTP route도 302 redirect 60,851건, 링크 생성·삭제 각 20건으로 일치했다.
- `miss_stale`은 0건이고 setup에서만 `miss_absent`와 risk check가 각 20건 발생했다.
- HTTP 5xx와 redirect error는 0건이었다.

## PostgreSQL

| 항목 | 결과 |
|---|---:|
| 225 RPS commit/s | 892.85 |
| 250 RPS commit/s | 1,011.80 |
| connection 최대 | 10 |
| 전체 commit 증가 | 243,548 |
| rollback / deadlock 증가 | 0 / 0 |
| cache hit ratio | 100% |
| temporary data 증가 | 0 B |
| CPU 최대 | 352m |
| 메모리 최대 | 173.77Mi |
| container read / write 최대 | 0 / 15.18MiB/s |

관측된 lock은 `AccessShareLock` 2개, `RowExclusiveLock` 4개,
`RowShareLock` 2개가 최대였다. exclusive 계열 lock, deadlock, rollback,
temporary data, Hikari pending과 timeout이 없어 DB 병목 징후는 없다.
따라서 상위 SQL 추가 분석은 수행하지 않았다.

## 판정

성공. 단일 dev Pod는 Warm-cache redirect 225 RPS와 250 RPS를 각각 2분 동안
목표 처리량 그대로 유지했다. 오류·드롭·restart·connection 대기 없이 서버 p99도
3.23ms 이하로 안정적이었다.

최대 지속 처리량은 아직 확인되지 않았다. 다음 테스트는 같은 조건에서 275 RPS를
단독으로 2분 검증하고, 통과할 때만 보정된 ramp와 VU 상한을 유지한 채 300 RPS로
진행한다.
