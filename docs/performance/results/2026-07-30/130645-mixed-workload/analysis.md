# Mixed workload replica 1 분석

## 판정

성공. 워밍업된 단일 Pod에서 모든 k6 threshold를 통과했다.

| 항목 | 결과 |
|---|---:|
| HTTP 실패 / dropped iteration | 0 / 0 |
| k6 redirect p95 / p99 | 16.44ms / 40.58ms |
| 서버 redirect p95 / p99 | 2.01ms / 2.54ms |
| redirect 수 | 63,268.40 |
| Pod CPU 최대 | 0.457 core |
| Hikari pending 최대 | 0 |
| Tomcat busy 최대 | 3 |
| Hikari timeout 증가 | 0 |
| PostgreSQL CPU 최대 | 0.457 core |

500 RPS에서는 replica 1도 충분한 여유가 있다. replica 2는 요청과 CPU를 절반씩
분산했지만 지연 개선은 없으므로 현재 운영 부하만으로 확장할 이유는 없다.
