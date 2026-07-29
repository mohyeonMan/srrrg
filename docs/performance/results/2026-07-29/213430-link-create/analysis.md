# SHA-256 링크 생성 cache-hit 15 RPS 분석

성공. 생성 520건, cache hit 519건과 setup의 miss·fixed-safe 검사 각 1건이
일치했다. k6 p95/p99는 59.12/93.87ms, 서버 p95/p99는 3.78/4.42ms였다.

앱 CPU는 평균 31m, 최대 68m였고 전체 CPU throttled period는 0.23%였다.
Hikari pending/timeout, PostgreSQL deadlock과 Pod restart는 모두 0이었다.

변경 전 동일 hit 15 RPS는 p95 7.90초, throttling 최대 96.11%와 dropped
iteration 60건으로 실패했다. SHA-256 변경 후 같은 부하는 정상 처리됐다.
