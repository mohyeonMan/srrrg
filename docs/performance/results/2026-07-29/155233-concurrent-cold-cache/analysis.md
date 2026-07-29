# Concurrent cold-cache 10 VU 분석

성공. 같은 대상 URL의 동시 redirect 10건이 모두 성공했다. `miss_stale`과 fixed-safe
검사도 각각 10건 증가해 중복 검사 배수는 10배였다. cache hit는 0건이었다.

k6 p95/p99는 237.12ms/237.30ms, 서버 p95/p99는 132.82ms/133.94ms였다.
Hikari timeout, Pod restart와 PostgreSQL deadlock은 0이었다.

현재 구현은 같은 URL의 동시 위험 검사를 합치지 않고 요청별로 수행한다.
