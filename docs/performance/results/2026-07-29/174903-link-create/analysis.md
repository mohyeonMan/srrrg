# 링크 생성 cache-miss 10 RPS 최초 기준 초과 분석

실패. HTTP 실패와 dropped iteration은 0이었지만 k6 p95가 324.65ms로 300ms
기준을 초과해 자동 중단됐다. k6 p99는 344.43ms, 서버 p95/p99는
278.17/346.18ms였고 fixed-safe p95는 111.26ms였다.

cache miss와 fixed-safe 검사는 서버 완료 327건과 일치했다. k6 완료 수보다 1건
많은 것은 threshold 중단 시 이미 서버로 전달된 요청이 완료된 결과다.

애플리케이션 전체 throttled period는 7.66%, Tomcat busy 최대 5였고 Hikari
pending/timeout, PostgreSQL deadlock, HTTP 5xx와 Pod restart는 모두 0이었다.
DB 포화보다는 fixed-safe 100ms 지연과 BCrypt를 포함한 전체 생성 경로가 p95 기준을
넘었다.

현재 자원과 100ms 외부 검사 조건에서 cache-miss 10 RPS는 실패다.
