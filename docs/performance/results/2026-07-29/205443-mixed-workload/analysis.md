# Mixed workload 500 + 4 + 1 RPS 분석

## 실행 조건

| 항목 | 값 |
|---|---|
| Warm-cache redirect | 500 RPS |
| Cache-hit 링크 생성 | 4 RPS |
| Cache-miss 링크 생성 | 1 RPS |
| 동시 부하 유지 | 2분 |
| 애플리케이션 | 1 Pod, CPU `250m/750m`, memory `512Mi/1Gi` |
| PostgreSQL | CPU `500m/1500m`, memory `512Mi/2Gi` |
| HikariCP maximum pool size | 10 |

## 결과

실패. k6 HTTP 실패는 180건(0.75%), dropped iteration은 39,681건이었다.
redirect p95/p99는 2.39/3.19초, cache-hit 생성은 2.39/3.17초,
cache-miss 생성은 3.23/3.49초로 모든 지연 기준을 초과했다.

Prometheus의 서버 redirect p95/p99도 2.37/3.12초로 k6 결과와 일치했다.
실제 redirect 처리량은 평균 147.51 RPS, 최대 239.80 RPS에 그쳐 목표 500 RPS를
유지하지 못했다. HTTP 500은 177건, redirect error는 173건, 링크 생성 error는
3건 증가했다.

## 병목

애플리케이션 CPU throttled period는 전체 구간 98.46%였고 30초 구간 최대는
100%였다. Tomcat busy thread는 평균 182.78, 최대 200으로 포화됐다.

CPU 포화 뒤 Hikari active가 최대 10, pending이 평균 165.96·최대 187까지
증가했고 connection timeout도 180건 발생했다. click 및 redirect 쓰기 p95는
각각 940.64ms와 854.37ms로 늘었다.

반면 PostgreSQL CPU는 최대 0.33 core였고 rollback과 deadlock은 0이었다.
링크 생성의 BCrypt는 DB 트랜잭션 밖에서 실행되므로 생성 요청이 connection을 직접
오래 보유한 것이 아니다. BCrypt가 포함된 생성 부하로 애플리케이션 CPU가 포화되고,
동시에 실행된 redirect 쓰기 트랜잭션이 느려져 Tomcat과 Hikari가 연쇄 포화된 것으로
판정한다.

## 판정

현재 단일 Pod 자원에서 `redirect 500 + link-create hit 4 + miss 1 RPS` 혼합 부하는
지원하지 못한다. VU나 Hikari pool만 늘리면 CPU 포화 상태의 대기 요청이 더 쌓이므로
재시험 조건으로 사용하지 않는다.

운영 목표 조합을 유지하려면 다음 테스트는 동일한 부하와 임계값에서 애플리케이션
replica 2개를 비교하는 것이 적절하다.
