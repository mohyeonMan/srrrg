# SHA-256 링크 생성 cache-hit 5 RPS 분석

성공. k6 p95/p99는 50.17/75.94ms, 서버 p95/p99는 5.76/6.99ms였다.
HTTP 실패와 dropped iteration은 0이었다.

앱 CPU는 평균 26m, 최대 81m였고 Hikari pending/timeout, PostgreSQL deadlock과
Pod restart는 0이었다. 새 Pod의 첫 scrape가 이미 생성 3건을 포함해 Prometheus
Counter로는 이후 194건만 직접 증명하며, 전체 완료 197건은 k6를 기준으로 한다.

변경 전 동일 hit 5 RPS의 앱 CPU 평균 225m에서 26m로 감소했다.
