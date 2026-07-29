# Warm-cache 750 RPS 분석

성공. 실제 처리량 749.96 RPS, 서버 p95/p99 2.97ms/3.92ms를 유지했다.
k6 HTTP 실패와 dropped iteration은 0이었다. 안정 구간 throttling 0.59%,
Hikari pending·timeout 0, PostgreSQL CPU 최대 882m와 deadlock 0으로
지속 병목은 관측되지 않았다.
