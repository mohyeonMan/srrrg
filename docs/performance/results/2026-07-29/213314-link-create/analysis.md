# SHA-256 링크 생성 cache-miss 10 RPS 분석

성공. 생성, cache miss와 fixed-safe 검사가 모두 358건으로 일치했다. k6 p95/p99는
140.65/162.26ms, 서버 p95/p99는 111.26/111.73ms였다.

앱 CPU는 평균 27m, 최대 58m였고 CPU throttling, Hikari pending/timeout,
PostgreSQL deadlock과 Pod restart는 모두 0이었다.

변경 전 동일 miss 10 RPS는 k6 p95 324.65ms로 실패했지만 이번 실행은 통과했다.
