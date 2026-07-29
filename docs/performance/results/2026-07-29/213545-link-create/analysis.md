# SHA-256 링크 생성 cache-miss 15 RPS 분석

성공. 생성, cache miss와 fixed-safe 검사가 모두 520건으로 일치했다. k6 p95/p99는
137.98/139.78ms, 서버 p95/p99는 111.28/111.75ms였다.

앱 CPU는 평균 25m, 최대 74m였고 전체 CPU throttled period는 1.18%였다.
Hikari pending/timeout, PostgreSQL deadlock과 Pod restart는 모두 0이었다.

현재 SHA-256 구현에서 cache-hit와 miss 모두 15 RPS까지 검증됐으며 최대 지속
처리량은 아직 확인되지 않았다.
