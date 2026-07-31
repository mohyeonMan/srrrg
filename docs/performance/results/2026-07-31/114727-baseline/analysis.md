# Baseline 분석

## k6 판정

성공. VU 1로 1분간 링크 생성, 관리 조회, warm-cache redirect와 삭제를 1,172회
반복했다. HTTP 4,690건과 check 8,207건이 모두 성공했다.

| endpoint | 평균 | p95 | p99 |
|---|---:|---:|---:|
| 링크 생성 | 13.75ms | 28.91ms | 36.16ms |
| 관리 조회 | 11.93ms | 25.31ms | 31.94ms |
| redirect | 11.59ms | 16.91ms | 23.72ms |

전체 HTTP p95/p99는 24.18/32.42ms였고 HTTP 실패는 0건이었다.

## 남은 분석

현재 로컬에 클러스터 Prometheus 접근 경로가 없어 CPU, Hikari, PostgreSQL 지표와
전체·Pod별 요청 수 정합성은 아직 조회하지 못했다. 해당 조회 전까지 metadata의
`analysisStatus`는 `pending`으로 유지한다.
