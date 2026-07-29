# 링크 생성 cache-hit 1 RPS 분석

링크 생성 66건이 모두 성공했다. setup의 최초 `miss_absent` 1건 이후 65건은 모두
cache hit였으며 HTTP 실패와 dropped iteration은 0이었다.

k6 p95/p99는 186.59/216.27ms, 서버 p95/p99는 74.94/141.82ms였다. 앱 CPU는
평균 89m, 최대 213m였고 PostgreSQL CPU 최대는 16m였다. Hikari pending/timeout과
PostgreSQL deadlock은 0이었다.

링크 생성 cache-hit 1 RPS 기준선을 통과했다.
