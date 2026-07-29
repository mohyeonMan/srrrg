# Cold-cache 5·10·20 RPS calibration 분석

## 실행 조건

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-29 14:47:13 ~ 14:49:33 KST |
| fixed-safe delay / cache duration | `0ms` / `1ms` |
| 워밍업 | 10 RPS, 30초 |
| 본 측정 | 5·10·20 RPS, 각 30초 |
| 애플리케이션 | dev 1 replica, CPU `250m/750m`, memory `512Mi/1Gi` |
| PostgreSQL | CPU `500m/1500m`, memory `512Mi/2Gi` |
| 애플리케이션 commit | `3611a3e5d534ac49a29112fcefdee44baed30c88` |
| 인프라 commit | `8edda51` |

## 결과

모든 k6 threshold를 통과했다. 전체 redirect 1,499건에서 HTTP 실패와 dropped
iteration은 0이었고 본 측정 클라이언트 p95/p99는 22.71ms/33ms였다.

| 목표 RPS | 실제 RPS | 서버 p95 | 서버 p99 | fixed-safe p95 |
|---:|---:|---:|---:|---:|
| 5 | 5.00 | 9.31ms | 11.53ms | 0.95ms |
| 10 | 10.00 | 7.11ms | 8.74ms | 0.95ms |
| 20 | 20.00 | 5.94ms | 8.04ms | 0.95ms |

## Cold-cache 상태 검증

- redirect 1,499건과 `miss_stale` 1,499건이 정확히 일치했다.
- setup 링크 20개의 `miss_absent`를 포함해 fixed-safe 검사는 총 1,519건이었다.
- cache hit는 0건이었다.
- HTTP route는 생성 20건, redirect 1,499건, 삭제 20건으로 k6와 일치했다.

`1ms` cache duration이 모든 redirect에서 만료 상태를 만들었으므로 Cold-cache
시나리오가 의도대로 동작했다.

앱 CPU 최대 263m, 메모리 최대 323.59Mi, Hikari active/pending 최대 1/0이었다.
PostgreSQL CPU 최대 43m, connection 최대 10이었고 rollback·deadlock과 Hikari
timeout은 모두 0이었다.

## 판정

성공. `delay=0ms` Cold-cache 경로의 낮은 부하 기준선과 cache miss 계측 일치 여부를
확인했다. 다음 단계는 같은 미검증 URL에 동시 요청을 보내 fixed-safe 중복 검사 배수를
확인하는 Concurrent cold-cache다.
