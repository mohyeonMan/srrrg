# Replica 2 링크 생성 cache-miss 최대 처리량

## 판정

1,800 RPS 성공. 2분 동안 HTTP 실패, dropped iteration과 Hikari timeout은 0이었다.

| 항목 | 결과 |
|---|---:|
| k6 p95 / p99 | 138.27ms / 248.23ms |
| 서버 p95 / p99 | 111.82ms / 204.88ms |
| Hikari pending 최대 | 11 / 87 |
| Tomcat busy 최대 | 139 / 200 |

1,900 RPS에서는 k6 p95/p99가 334.16/522.29ms로 모두 기준을 초과했다. 따라서
cache-miss 최대 지속 처리량은 1,800 RPS, 실패 경계는 1,900 RPS로 판단한다.
