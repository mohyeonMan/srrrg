# Warm-cache 800 RPS 분석

성공. 단일 Pod가 실제 800.04 RPS를 2분 유지했고 서버 p95/p99는
3.02ms/4.45ms였다. k6 HTTP 실패와 dropped iteration은 0이었다.

Hikari active는 최대 10까지 사용됐지만 pending과 timeout은 0이었다. 순간 throttling
최대 17.33%가 처음 관측됐으나 안정 시점은 0.90%였고 처리량과 지연은 유지됐다.
PostgreSQL CPU 최대는 1.00 core로 1.5 core limit 이내였고 deadlock은 0이었다.
현재 조건에서 검증된 단일 Pod 최대 지속 처리량은 800 RPS다.
