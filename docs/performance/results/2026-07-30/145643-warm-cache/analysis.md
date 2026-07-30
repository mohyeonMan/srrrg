# Warm-cache replica 2 최대 처리량 분석

## 판정

1,850 RPS 성공. 2분 동안 목표 처리량을 유지했고 HTTP 실패, dropped iteration,
Hikari timeout과 Pod restart는 0이었다.

| 항목 | 결과 |
|---|---:|
| k6 redirect p95 / p99 | 18.04ms / 32.39ms |
| 서버 redirect p95 / p99 | 2.73ms / 5.32ms |
| access 쓰기 p95 | 1.94ms |
| 실제 처리량 | 1,850.02 RPS |
| Pod별 처리량 | 925.01 / 925.01 RPS |
| Hikari pending 최대 | 9 / 0 |
| Tomcat busy 최대 | 12 / 6 |
| Hikari timeout 증가 | 0 |

1,875 RPS에서는 약 20초 안에 한 Pod의 Hikari pending과 Tomcat busy가 각각
99와 105까지 증가하면서 k6 p95가 107.57ms로 실패했다. 따라서 현재 자원의
최대 지속 처리량은 1,850 RPS, 실패 경계는 1,875 RPS로 판단한다.
