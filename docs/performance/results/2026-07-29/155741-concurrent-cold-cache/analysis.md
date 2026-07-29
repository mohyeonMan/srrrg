# Concurrent cold-cache 25 VU 분석

성공. 같은 대상 URL의 동시 redirect 25건이 모두 성공했다. `miss_stale`과 fixed-safe
검사도 각각 25건 증가해 중복 검사 배수는 25배였다. cache hit는 0건이었다.

k6 p95/p99는 184.76ms/184.76ms, 서버 p95/p99는 133.92ms/150.99ms였다.
Hikari timeout과 PostgreSQL deadlock은 0이었다.
