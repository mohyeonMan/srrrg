# SHA-256 Mixed workload 첫 재시험 분석

실패. k6 redirect p95/p99는 1.02/1.43초, dropped iteration은 13,049건이었다.
서버 redirect p95/p99도 992.18/1,394.08ms로 초기 포화를 확인했다.

CPU throttled period는 전체 75.89%, 30초 최대 100%였고 Tomcat busy 최대 198,
Hikari pending 최대 189였다. 다만 실행 후반에는 redirect 499.88 RPS,
Tomcat busy 5와 Hikari pending 0으로 회복했다. 변경 전 Mixed 실행은 종료 시에도
pending 167이 남았지만 이번 실행은 정상 상태로 복귀했다.

HTTP 실패는 180건에서 1건, dropped iteration은 39,681건에서 13,049건으로
감소했다. SHA-256 변경 효과는 확인됐지만 첫 재시험 전체 threshold는 통과하지
못했으므로 워밍된 상태에서 동일 조건을 다시 실행한다.
