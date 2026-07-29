# Warm-cache 700 RPS 분석

성공. 실제 처리량 699.92 RPS, 서버 p95/p99 2.91ms/3.68ms를 유지했다.
k6 HTTP 실패와 dropped iteration은 0이었다. 순간 throttling 최대는 5.29%였지만
안정 구간은 0.54%였고 지연 악화가 없었다. Hikari pending·timeout, Pod restart,
PostgreSQL deadlock도 모두 0이었다.
