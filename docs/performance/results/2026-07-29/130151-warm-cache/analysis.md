# Warm-cache 350~500 RPS 단계 테스트 분석

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-29 13:01:51 ~ 13:11:07 KST |
| 워밍업 | 기존 로직 그대로 10 RPS, 30초 |
| 본 측정 | 350·400·450·500 RPS, 각 2분 유지 |
| 애플리케이션 | dev 1 replica, CPU `250m/750m`, memory `512Mi/1Gi` |
| PostgreSQL | CPU `500m/1500m`, memory `512Mi/2Gi` |
| HikariCP maximum pool size | 10 |
| 애플리케이션 commit | `b41e3a6d090ae5f5f203c5bc1994d645224283b1` |
| 인프라 commit | `783b435b51d12e6bc8a4062084cefddbf7bba7ec` |
| k6 종료 코드 | 0 |

재배포 직후 첫 시도는 성능 판정에서 제외하고, 기존 10 RPS 30초 워밍업 이후
애플리케이션과 DB가 한 번 부하를 받은 상태에서 본 실행을 수행했다. 워밍업 로직은
수정하지 않았다.

## k6 결과

| 항목 | 결과 |
|---|---:|
| 전체 redirect | 218,849 |
| 전체 HTTP 요청 | 218,889 |
| check | 218,949 / 218,949 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| 본 측정 p95 / p99 | 18.92ms / 51.36ms |
| 최대 활성 VU | 35 |

모든 threshold를 통과했고 테스트 링크 20개도 전부 정리됐다.

## Prometheus 직접 조회 결과

| 목표 RPS | 실제 RPS | 서버 p50 | 서버 p95 | 서버 p99 |
|---:|---:|---:|---:|---:|
| 350 | 350.04 | 1.98ms | 2.67ms | 3.24ms |
| 400 | 399.92 | 2.00ms | 2.74ms | 3.56ms |
| 450 | 450.00 | 1.99ms | 2.71ms | 3.39ms |
| 500 | 500.00 | 2.01ms | 2.84ms | 4.03ms |

| 목표 RPS | 앱 CPU 평균 / 최대 | 앱 메모리 최대 | Hikari active / pending 최대 |
|---:|---:|---:|---:|
| 350 | 246m / 456m | 388.82Mi | 10 / 3 |
| 400 | 305m / 472m | 391.66Mi | 2 / 0 |
| 450 | 343m / 530m | 396.51Mi | 3 / 0 |
| 500 | 369m / 557m | 401.75Mi | 6 / 0 |

350 RPS의 Hikari 최대치는 부하 초기 구간의 순간값이다. 400 RPS부터 500 RPS까지
pending은 0이었고 timeout도 발생하지 않았다. 각 단계의 안정 구간 CPU throttling은
0%였다. 전체 실행의 throttled period는 5,560개 중 61개(1.10%)였으나 처리량과
지연의 악화는 없었다.

500 RPS에서 애플리케이션 CPU는 평균 369m, 최대 557m로 750m limit의 약 49%와
74%를 사용했다. 메모리는 전체 실행 최대 402.84Mi로 1Gi limit에 충분한 여유가
있었고, heap 최대 92.31Mi와 평균 GC pause 최대 2.2ms도 안정적이었다.

## 요청 수·캐시·PostgreSQL 검증

- k6 redirect, Prometheus redirect 및 cache hit 증가량이 모두 218,849건으로 일치했다.
- 링크 생성·삭제는 각 20건, cache miss와 `fixed-safe` 검사는 setup에서만 각 20건이었다.
- HTTP 4xx·5xx, Pod restart, Hikari timeout은 모두 0건이었다.
- PostgreSQL commit은 875,697건 증가했고 rollback, deadlock, temporary data 증가는 0이었다.
- PostgreSQL connection 최대 10, CPU 최대 565m, 메모리 최대 166.20Mi였다.
- PostgreSQL container I/O 최대는 read 2.73MiB/s, write 28.57MiB/s였다.
- exclusive 계열 lock은 없었다.

500 RPS에서 PostgreSQL은 평균 CPU 321m, 최대 557m로 1.5 CPU limit에 여유가
있었다. cache hit ratio가 98.82%로 내려갔지만 서버 p95·p99와 쓰기 지연은 안정적이고
물리 I/O도 과도하지 않았다. 따라서 이번 실행에서는 상위 SQL이나 autovacuum 같은
추가 진단이 필요하지 않다.

## 판정

성공. 변경 리소스의 단일 dev Pod는 Warm-cache redirect 500 RPS까지 목표 처리량을
2분 동안 유지했다. 500 TPS 운영 목표 기준으로 애플리케이션과 PostgreSQL 모두
즉시 증설이 필요한 병목 징후는 없다.

이 결과는 단일 Pod의 변경 후 기준선으로 사용한다. 다음 순서는 replica를 바로 늘리는
것이 아니라 문서 계획대로 단일 Pod의 Warm-cache 최대 지속 처리량을 먼저 확인하는
것이다. 이후 Cold-cache, Mixed, Spike, Soak를 거친 뒤 같은 설정의 replica 1·2
비교를 별도 실행한다.
