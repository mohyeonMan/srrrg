# 링크 생성 cache-hit 15 RPS 최초 기준 초과 분석

실패. k6 p95/p99가 7.90/8.86초로 기준 300/500ms를 크게 초과했고 dropped
iteration 60건으로 자동 중단됐다. 서버 p95는 8.67초였고 p99는 histogram 상한
10초에 도달했다.

전체 구간 CPU throttled period는 53.93%, 30초 구간 최대는 96.11%였고 Tomcat
busy thread가 68까지 증가했다. 반면 Hikari pending/timeout은 0, PostgreSQL CPU
최대는 16m, deadlock은 0이었다. 따라서 병목은 DB나 connection pool이 아니라
BCrypt를 포함한 링크 생성 경로의 애플리케이션 CPU 제한과 그에 따른 요청 적체다.

k6는 중단 전 270건을 완료했지만 이미 서버로 전달된 요청이 이후 처리돼 Prometheus
링크 생성 Counter는 350건 증가했다. 오류가 없는 대신 요청이 쌓인 결과다.

현재 자원에서 cache-hit 15 RPS는 실패다. 이후 12·13·14 RPS 재시험으로 통과 상한을
확인한다.
