# 링크 생성 cache-hit 2 RPS 분석

링크 생성 99건이 모두 성공했다. setup의 최초 `miss_absent` 1건 이후 98건은 모두
cache hit였으며 HTTP 실패와 dropped iteration은 0이었다.

k6 p95/p99는 168.33/248.72ms, 서버 p95/p99는 55.75/156.81ms였다. 앱 CPU는
평균 100m, 최대 437m였고 PostgreSQL CPU 최대는 18m였다. Hikari pending/timeout과
PostgreSQL deadlock은 0이었다.

링크 생성 cache-hit 2 RPS를 통과했다.
