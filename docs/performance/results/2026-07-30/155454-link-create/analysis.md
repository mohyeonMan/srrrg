# Replica 2 링크 생성 cache-hit 최대 처리량

## 판정

2,200 RPS 성공. 2분 동안 HTTP 실패, dropped iteration과 Hikari timeout은 0이었다.

| 항목 | 결과 |
|---|---:|
| k6 p95 / p99 | 42.50ms / 441.47ms |
| 서버 p95 / p99 | 2.44ms / 154.17ms |
| Hikari pending 최대 | 162 / 0 |
| Tomcat busy 최대 | 169 / 10 |

2,300 RPS에서는 k6 p99가 574.21ms로 기준을 초과했다. 따라서 cache-hit 최대 지속
처리량은 2,200 RPS, 실패 경계는 2,300 RPS로 판단한다.
