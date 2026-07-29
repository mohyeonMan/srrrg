# 링크 생성 cache-hit 5 RPS 재검증 분석

성공. k6 완료 196건에서 HTTP 실패와 dropped iteration은 0이었고
p95/p99는 78.75/83.30ms였다. 서버 p95/p99는 55.75/89.47ms였다.

애플리케이션 CPU는 평균 111m, 30초 rate 최대 336m였고 전체 throttled period는
1.55%였다. Tomcat busy 최대 2, Hikari pending/timeout과 PostgreSQL deadlock,
HTTP 5xx와 Pod restart는 모두 0이었다.

이 실행에서 애플리케이션 시계열은 테스트 시작 후 처음 생성됐다. 첫 scrape가 이미
5건을 포함해 Prometheus Counter로는 이후 191건만 증명할 수 있고, 전체 196건은 k6
완료 수를 기준으로 한다. 이 수집 경계 한계를 제외하면 cache-hit 5 RPS 재검증을
통과했다.
