# Replica 2 링크 생성 cache-miss 실패 경계

1,900 RPS 실패. HTTP 실패와 dropped iteration은 없었지만 k6 p95/p99가
334.16/522.29ms로 기준을 초과했다. 두 Pod의 Tomcat busy가 모두 200에 도달했고,
Hikari pending도 101/73까지 증가했다.
