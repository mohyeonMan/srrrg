# 링크 생성 cache-hit 5 RPS 분석

링크 생성 197건이 모두 성공했다. setup의 최초 `miss_absent` 1건 이후 196건은 모두
cache hit였으며 HTTP 실패와 dropped iteration은 0이었다.

k6 p95/p99는 71.87/95.65ms, 서버 p95/p99는 55.81/106.07ms였다. 앱 CPU는
평균 225m, 20초 rate 기준 최대 813m였고 PostgreSQL CPU 최대는 24m였다.
Hikari pending/timeout과 PostgreSQL deadlock은 0이었다.

링크 생성 cache-hit 5 RPS를 통과했다. 앱 CPU 순간치는 750m limit 경계까지
도달했으므로 이 결과만으로 5 RPS보다 높은 지속 처리량을 보장하지 않는다.
