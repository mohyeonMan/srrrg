# SHA-256 링크 생성 cache-hit 10 RPS 분석

성공. 생성 359건, cache hit 358건과 setup의 miss·fixed-safe 검사 각 1건이
일치했다. k6 p95/p99는 49.07/78.71ms, 서버 p95/p99는 4.80/5.43ms였다.

앱 CPU는 평균 21m, 최대 62m였고 CPU throttling, Hikari pending/timeout,
PostgreSQL deadlock과 Pod restart는 모두 0이었다.
