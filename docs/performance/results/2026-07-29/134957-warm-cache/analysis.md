# Warm-cache 650 RPS 분석

성공. 실제 처리량 649.96 RPS, 서버 p95/p99 2.87ms/3.47ms를 유지했다.
k6 HTTP 실패와 dropped iteration은 0이었다. 앱 CPU 30초 rate 최대가 797m로
limit 주변까지 닿았지만 안정 구간 throttling은 0.33%였고 Hikari pending·timeout,
Pod restart와 PostgreSQL deadlock은 모두 0이었다.
