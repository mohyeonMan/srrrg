# SHA-256 링크 생성 cache-miss 5 RPS 분석

성공. 생성, cache miss와 fixed-safe 검사가 모두 195건으로 일치했다. k6 p95/p99는
210.45/466.32ms, 서버 p95/p99는 111.26/111.73ms였다. k6 p99에는 클라이언트
tail이 있었지만 500ms 기준 이내이고 서버 histogram은 안정적이었다.

앱 CPU는 평균 24m, 최대 66m였고 CPU throttling, Hikari pending/timeout,
PostgreSQL deadlock과 Pod restart는 모두 0이었다.
