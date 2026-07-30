# Smoke test 분석

## 판정

성공. 전체 HTTP 요청 23건과 check 48건이 모두 성공했고 HTTP 실패는 0건이었다.

## 결과

| 항목 | 결과 |
|---|---:|
| 링크 생성 | 1건 |
| 관리 조회 | 1건 |
| redirect | 20건 |
| 링크 삭제 | 1건 |
| HTTP 평균 / p95 / 최대 | 48.23ms / 125.36ms / 424.68ms |
| access 쓰기 성공 메트릭 | 20건 |
| Hikari active / pending / timeout | 0 / 0 / 0 |
| Tomcat error | 0 |

접근 이벤트 쓰기 메트릭은 redirect 20건과 정확히 일치했다. 위험 검사 cache miss와
fixed-safe 검사는 각각 1건, 이후 cache hit는 20건으로 의도한 흐름과 일치했다.
