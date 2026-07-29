# 링크 생성 cache-hit 10 RPS 분석

성공. 링크 생성 359건과 cache hit 358건, setup의 miss 및 fixed-safe 검사 각 1건이
일치했다. HTTP 실패와 dropped iteration은 0이었다.

k6 p95/p99는 76.66/91.95ms, 서버 p95/p99는 55.67/62.91ms였다. 애플리케이션
CPU는 평균 169m, 30초 rate 최대 723m였고 전체 throttled period는 1.48%였다.
Tomcat busy 최대 2, Hikari pending/timeout과 PostgreSQL deadlock, HTTP 5xx와
Pod restart는 모두 0이었다.

링크 생성 cache-hit 10 RPS를 통과했다.
