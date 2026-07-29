# Warm-cache 600 RPS 분석

성공. 실제 처리량 600.04 RPS, 서버 p95/p99 2.85ms/3.44ms를 유지했다.
k6 HTTP 실패와 dropped iteration, Hikari pending·timeout, Pod restart,
PostgreSQL deadlock은 모두 0이었다. 앱 CPU 최대 611m, 안정 구간 throttling 0%,
PostgreSQL CPU 최대 638m로 다음 단계 진행 조건을 충족했다.
