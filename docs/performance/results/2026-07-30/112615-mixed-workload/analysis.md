# Mixed workload 2분 회귀 분석

## 판정

성공. redirect 500 RPS, cache-hit 링크 생성 4 RPS, cache-miss 링크 생성 1 RPS를
2분간 유지했으며 모든 threshold를 통과했다.

## 결과

| 항목 | 결과 |
|---|---:|
| HTTP 실패 / dropped iteration | 0 / 0 |
| k6 redirect p95 / p99 | 15.16ms / 22.54ms |
| 서버 redirect p95 / p99 | 2.02ms / 2.49ms |
| access 쓰기 p95 / p99 | 1.56ms / 1.74ms |
| Hikari active 최대 / pending 최대 | 2 / 0 |
| Hikari timeout 증가 | 0 |
| Tomcat busy 최대 | 2 |
| HTTP 5xx / Pod restart | 0 / 0 |
| DB rollback / deadlock 증가 | 0 / 0 |

Prometheus HTTP API에서 실행 구간을 직접 조회했다. 단일 access 쓰기 트랜잭션은 낮은
지연을 유지했고 connection, thread 및 DB 오류 징후가 없었다.
