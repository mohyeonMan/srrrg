# 링크 생성 cache-miss 8 RPS 분석

성공. 링크 생성, cache miss와 fixed-safe 검사가 모두 293건으로 일치했고 cache
hit는 0이었다. HTTP 실패와 dropped iteration은 0이었다.

k6 p95/p99는 192.57/236.76ms, 서버 p95/p99는 156.39/212.92ms였고
fixed-safe p95는 111.26ms였다. 애플리케이션 전체 throttled period는 1.70%,
Tomcat busy 최대 2였다. Hikari pending/timeout과 PostgreSQL deadlock,
HTTP 5xx와 Pod restart는 모두 0이었다.

링크 생성 cache-miss 8 RPS를 통과했다. 10 RPS가 p95 기준을 초과했으므로 현재
조건의 검증된 최대 지속 처리량은 8 RPS다.
