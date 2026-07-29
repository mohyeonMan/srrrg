# 링크 생성 cache-hit 14 RPS 분석

성공. 링크 생성 488건과 cache hit 487건, setup의 miss 및 fixed-safe 검사 각 1건이
일치했다. HTTP 실패와 dropped iteration은 0이었다.

k6 p95/p99는 112.61/247.38ms, 서버 p95/p99는 92.63/225.04ms였다.
애플리케이션 전체 throttled period는 22.94%, 30초 구간 최대는 37.60%였고
Tomcat busy 최대는 5였다. Hikari pending/timeout과 PostgreSQL deadlock,
HTTP 5xx와 Pod restart는 모두 0이었다.

링크 생성 cache-hit 14 RPS를 통과했다. 다만 CPU throttling이 이미 뚜렷하고
15 RPS에서 요청 적체가 발생하므로 현재 자원의 검증된 최대 지속 처리량은 14 RPS다.
