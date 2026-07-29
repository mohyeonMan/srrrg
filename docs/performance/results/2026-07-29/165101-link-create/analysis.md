# 링크 생성 cache-miss 2 RPS 분석

링크 생성, `miss_absent`와 fixed-safe 검사가 각각 98건으로 일치했다. 모든 요청이
성공했고 HTTP 실패와 dropped iteration은 0이었다.

k6 p95/p99는 236.10/277.75ms, 서버 p95/p99는 161.51/201.77ms였고 fixed-safe
p95는 111.26ms였다. 앱 CPU는 평균 119m, 최대 245m였고 PostgreSQL CPU 최대는
21m였다. Hikari pending/timeout과 PostgreSQL deadlock은 0이었다.

링크 생성 cache-miss 2 RPS를 통과했다.
