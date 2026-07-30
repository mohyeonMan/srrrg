# Mixed workload 30분 Soak 분석

## 판정

성공(일시적 포화 경고 1회). k6 threshold는 모두 통과했고, 한 번의 5초 scrape에서
Hikari pending 89와 Tomcat busy 100이 관측됐지만 다음 scrape에서 복구됐다.
HTTP 실패, dropped iteration, connection timeout과 Pod restart는 없었다.

## 전체 결과

| 항목 | 결과 |
|---|---:|
| HTTP 실패 / dropped iteration | 0 / 0 |
| k6 redirect p95 / p99 | 18.97ms / 51.94ms |
| 서버 redirect p95 / p99 | 2.07ms / 3.55ms |
| access 쓰기 p95 / p99 | 1.59ms / 1.93ms |
| Hikari timeout 증가 | 0 |
| HTTP 5xx / Pod restart | 0 / 0 |
| DB rollback / deadlock 증가 | 0 / 0 |

## 일시적 포화

11:49:58 KST 한 표본에서 Hikari active/pending 9/89, Tomcat busy 100이 함께
관측됐다. 같은 구간의 20초 서버 redirect p95 최대는 492.91ms였고 앱 CPU는
0.83 core, CPU throttled period는 최대 20.10%였다. 다음 scrape에서 pending은
0으로 회복했으며 HTTP 실패, dropped iteration과 timeout으로 이어지지는 않았다.

GC pause의 20초 누계 최대는 93ms로 긴 stop-the-world pause가 직접 원인인 증거는
부족하다. PostgreSQL deadlock과 rollback은 없었으나 현재 exporter만으로 과거
`transactionid`, `WALSync`, `WALWrite` wait event를 직접 복원할 수는 없다.

## 저장량

실행 후 누적 access event는 1,079,266건, 전체 DB 크기는 234,404,887바이트였다.
이 값에는 배포 이후 Smoke와 앞선 Mixed 실행 데이터도 포함된다.
