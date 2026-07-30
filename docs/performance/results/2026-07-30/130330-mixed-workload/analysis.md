# Mixed workload replica 2 분석

## 판정

성공. 워밍업된 Pod 2개에 redirect 요청이 49.996% / 50.004%로 균등하게 분배됐고
모든 k6 threshold를 통과했다.

| 항목 | 결과 |
|---|---:|
| HTTP 실패 / dropped iteration | 0 / 0 |
| k6 redirect p95 / p99 | 18.34ms / 44.80ms |
| 서버 redirect p95 / p99 | 2.20ms / 2.87ms |
| Pod별 redirect 수 | 31,609.76 / 31,614.96 |
| Pod별 CPU 최대 | 0.326 / 0.323 core |
| Pod별 Hikari pending 최대 | 0 / 0 |
| Pod별 Tomcat busy 최대 | 2 / 2 |
| Hikari timeout 증가 | 0 |
| PostgreSQL CPU 최대 | 0.397 core |

새 Pod를 즉시 투입한 첫 실행은 cold-start 영향으로 실패했다. 충분히 워밍업한 뒤
동일 조건으로 재실행한 이 결과를 replica 비교값으로 사용한다.
