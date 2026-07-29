# Warm-cache 550 RPS 분석

성공. 기존 10 RPS 30초 워밍업 후 단일 Pod가 550 RPS를 2분 유지했다.
k6 HTTP 실패와 dropped iteration은 0이며 p95/p99는 15.28ms/38.78ms였다.
Prometheus의 실제 처리량은 550 RPS, 서버 p95/p99는 3.01ms/4.88ms였다.
앱 CPU 최대 651m, 안정 구간 throttling 0.4%, Hikari pending·timeout 0,
PostgreSQL CPU 최대 599m, deadlock 0으로 다음 단계 진행 조건을 충족했다.
