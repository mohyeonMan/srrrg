# Mixed workload 성능 테스트

## 목적

단일 경로 테스트에서 검증한 운영 부하를 동시에 적용해 링크 생성의 SHA-256 해시와
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

500 redirect RPS, 30분 Soak에서는 다음 중 하나라도 충족하면 실패한다.

- 전체 redirect p95 100ms 이상 또는 p99 250ms 이상
- 링크 생성 p95 300ms 이상 또는 p99 500ms 이상
- HTTP 실패율 0.01% 초과 또는 dropped iteration 발생
- Hikari connection timeout, PostgreSQL deadlock 또는 Pod restart 발생
- Hikari pending이 15초 이상 연속 발생
- Tomcat busy가 90% 이상으로 1분 이상 지속
- DB lock/WAL 대기와 redirect 지연이 15초 이상 함께 지속
- cache-hit 생성 결과가 hit가 아니거나 cache-miss 생성 결과가 miss 및 fixed-safe 검사와 불일치

다음은 실패가 아닌 경고로 기록한다.

- Hikari pending이 발생했지만 15초 안에 복구
- 20초 구간 redirect p95가 250ms 이상
- CPU throttling이 10% 이상으로 5분 지속
- Tomcat busy 80% 이상이 반복 발생

실행 후 공용 결과 디렉터리에 Prometheus 조회 결과와 분석을 저장한 뒤 완료 판정한다.
측정 중 생성된 링크는 run ID가 포함된 원본 URL을 기준으로 별도 정리한다.
