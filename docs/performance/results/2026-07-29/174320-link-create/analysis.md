# 링크 생성 cache-hit 13 RPS 분석

성공. 링크 생성 455건과 cache hit 454건, setup의 miss 및 fixed-safe 검사 각 1건이
일치했다. HTTP 실패와 dropped iteration은 0이었다.

k6 p95/p99는 103.70/218.70ms, 서버 p95/p99는 88.36/205.35ms였다.
애플리케이션 전체 throttled period는 12.81%, 30초 구간 최대는 20.66%였지만
Tomcat busy 최대 2로 적체되지 않았다. Hikari pending/timeout과 PostgreSQL
deadlock, HTTP 5xx와 Pod restart는 모두 0이었다.

링크 생성 cache-hit 13 RPS를 통과했다.
