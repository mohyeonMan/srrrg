# Mixed workload 성능 테스트

## 목적

단일 경로 테스트에서 검증한 운영 부하를 동시에 적용해 링크 생성의 BCrypt와
URL 위험 검사가 warm-cache redirect 지연 및 DB connection에 미치는 영향을 확인한다.

## 기본 부하

| 경로 | 부하 |
|---|---:|
| Warm-cache redirect | 500 RPS |
| Cache-hit 링크 생성 | 4 RPS |
| Cache-miss 링크 생성 | 1 RPS |

10 RPS redirect를 30초 워밍업한 뒤 10초 동안 500 RPS로 올린다. 500 RPS에 도달하면
두 링크 생성 시나리오를 함께 시작해 2분 유지한다. setup에서 redirect 링크 20개와
cache-hit URL을 미리 생성한다.

## 실행

```bash
bash scripts/performance/run.sh mixed-workload
```

기본값은 `MIXED_REDIRECT_RATE`, `MIXED_HIT_CREATE_RATE`,
`MIXED_MISS_CREATE_RATE`, `MIXED_DURATION`으로 변경할 수 있다.

## 판정

- redirect p95 100ms, p99 250ms 미만
- 링크 생성 p95 300ms, p99 500ms 미만
- HTTP 실패와 dropped iteration 0
- Hikari pending/timeout, PostgreSQL deadlock과 Pod restart 0
- cache-hit 생성은 hit, cache-miss 생성은 miss 및 fixed-safe 검사와 일치

실행 후 공용 결과 디렉터리에 Prometheus 조회 결과와 분석을 저장한 뒤 완료 판정한다.
측정 중 생성된 링크는 run ID가 포함된 원본 URL을 기준으로 별도 정리한다.
