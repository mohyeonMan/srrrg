# Baseline 분석

## k6 판정

성공. VU 1로 1분간 링크 생성, 관리 조회, warm-cache redirect와 삭제를 1,172회
반복했다. HTTP 4,690건과 check 8,207건이 모두 성공했다.

| endpoint | 평균 | p95 | p99 |
|---|---:|---:|---:|
| 링크 생성 | 13.75ms | 28.91ms | 36.16ms |
| 관리 조회 | 11.93ms | 25.31ms | 31.94ms |
| redirect | 11.59ms | 16.91ms | 23.72ms |

전체 HTTP p95/p99는 24.18/32.42ms였고 HTTP 실패는 0건이었다.

## Prometheus

서버 p50/p95/p99는 링크 생성 1.39/2.04/2.35ms, 관리 조회
0.53/1.03/1.31ms, redirect 2.11/2.68/2.96ms였다. 서버 HTTP 증가량 4,690건은
k6 요청 수와 일치했고 두 Pod가 각각 2,345건을 처리했다.

Pod별 CPU 평균은 0.027/0.024 core, 최대는 0.076/0.063 core였다. Hikari
active 최대는 1/0, pending은 모두 0, Tomcat busy 최대는 2/1이었다.
PostgreSQL CPU 평균/최대는 0.031/0.092 core, connection 최대는 20이었다.
HTTP 5xx, Hikari timeout, Pod restart, DB rollback과 deadlock은 모두 0이었다.

## 판정

현재 배포된 replica 2 환경의 낮은 부하 기준값으로 사용한다. 문서 계획의 replica 1
기준선이 필요하면 replica 수만 1로 바꾼 뒤 같은 시나리오를 별도로 실행한다.
