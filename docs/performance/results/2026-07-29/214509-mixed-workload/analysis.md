# SHA-256 Mixed workload 워밍 후 재시험 분석

## 판정

k6 기준 실패. HTTP 실패는 0이고 모든 check가 성공했지만 dropped iteration
1,083건, redirect p99 392.83ms와 cache-miss 생성 p99 1.54초로 threshold를
초과했다. 각 p95는 redirect 68.52ms, hit 생성 83.06ms, miss 생성 207.15ms로
모두 통과했다.

## 서버 상태

Prometheus 서버 redirect p95/p99는 7.67/22.52ms, 링크 생성 p95/p99는
109.28/111.75ms였다. CPU throttled period는 전체 3.23%, 30초 최대 9.80%,
Tomcat busy 최대 4, Hikari active/pending 최대 2/0이었다. Hikari timeout,
HTTP 5xx, PostgreSQL deadlock과 Pod restart도 0이었다.

서버는 500 RPS를 안정 구간에서 유지했고 포화 징후가 없다. 반면 k6에서는 최대
6.84초의 client tail로 redirect VU 200이 종료 직전 소진됐다. 따라서 이번 실패는
애플리케이션 처리 한계가 아니라 현재 부하 생성기 또는 네트워크 tail을 분리하지
못한 테스트 실행기 한계로 기록한다.

SHA-256 변경으로 기존 Mixed 실행의 CPU·Tomcat·Hikari 연쇄 포화는 해소됐다.
다만 k6 threshold 전체를 통과하지 않았으므로 Mixed workload를 성공으로 판정하지
않는다.
