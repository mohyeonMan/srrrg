# 링크 생성 cache-miss 5 RPS 분석

링크 생성, `miss_absent`와 fixed-safe 검사가 각각 195건으로 일치했다. 모든 요청이
성공했고 HTTP 실패와 dropped iteration은 0이었다.

k6 p95/p99는 189.84/203.41ms, 서버 p95/p99는 156.51/174.16ms였고 fixed-safe
p95는 111.26ms였다. 앱 CPU는 평균 204m, 최대 626m였고 PostgreSQL CPU는 평균
11m, 최대 28m였다. Hikari pending/timeout과 PostgreSQL deadlock은 0이었다.

링크 생성 cache-miss 5 RPS를 통과했다.
