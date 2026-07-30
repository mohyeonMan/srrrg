# Warm-cache replica 2 실패 경계 분석

## 판정

1,875 RPS 실패. HTTP 실패와 dropped iteration은 없었지만 redirect p95가
107.57ms로 100ms 기준을 초과해 테스트가 조기 종료됐다.

같은 조건의 1,850 RPS는 2분 동안 통과했으므로 최대 지속 처리량은 1,850 RPS로
판정한다. 상세 Prometheus 비교는 `145643-warm-cache/prometheus-result.json`에
기록했다.
