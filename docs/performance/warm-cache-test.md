# Warm-cache redirect 단계별 부하 테스트

## 목적

URL 위험 검사 캐시가 유효한 redirect 경로에 일정한 arrival rate를 가해 지연, 오류,
DB 쓰기와 connection 상태를 확인한다. 첫 실행은 낮은 요청률에서 계측과 판정 기준이
정상 동작하는지 확인하는 calibration 테스트다.

## 기본 설정

| 항목 | 값 |
|---|---|
| 요청률 | 5, 10, 20 RPS |
| 각 요청률 유지 시간 | 30초 |
| 요청률 증가 시간 | 5초 |
| 측정 전 워밍업 | 10 RPS, 30초 |
| 테스트 링크 | 20개 |
| 초기 판정 기준 | p95 100ms 미만, p99 250ms 미만 |
| 예상하지 않은 HTTP 실패율 | 0.1% 미만 |
| dropped iteration | 0 |

setup에서 서로 다른 원본 URL을 사용하는 링크를 생성해 위험 검사 캐시를 채운다.
본 측정 전에 10 RPS로 30초 동안 redirect 경로를 워밍업한다. 워밍업 요청은
`endpoint=redirect_warm_cache_warmup`으로 분리해 본 측정 p95와 p99 threshold에서
제외하지만 check와 HTTP 실패율에는 포함한다.

부하 구간에서는 링크를 순환 선택하고 redirect를 따라가지 않은 채 302 응답만 확인한다.
teardown에서 생성한 링크를 삭제한다.

전체 실행 시간은 테스트 환경의 URL 위험 검사 cache duration보다 짧아야 한다.
더 긴 단계를 실행하려면 `SRRRG_URL_RISK_FIXED_SAFE_CACHE_DURATION`을 전체 실행 시간보다
길게 설정한 뒤 Pod가 새 설정으로 배포됐는지 확인한다. 그렇지 않으면 후반 구간에
`miss_stale`과 risk check가 섞여 순수 warm-cache 결과가 아니게 된다.
현재 dev 배포 매니페스트의 fixed-safe cache duration은 15분이다.

arrival-rate 실행기의 기본 pre-allocated VU는 최대 RPS의 2배, 최대 VU는 4배다.
load generator의 일시적인 네트워크 지연으로 dropped iteration이 발생하면
`WARM_PRE_ALLOCATED_VUS`와 `WARM_MAX_VUS`로 더 크게 지정할 수 있다.

redirect가 기본 250ms 이상 걸리면 k6 로그에 요청 시각과 blocked, connection, TLS,
sending, waiting, receiving 시간을 남긴다. 기준은 `WARM_SLOW_REQUEST_THRESHOLD_MS`로
변경할 수 있으며 각 구간의 전체 분포도 k6 실행 요약에 표시된다.

## 실행

Windows:

```powershell
.\scripts\performance\run.ps1 -Scenario warm-cache
```

macOS 또는 Linux:

```bash
bash scripts/performance/run.sh warm-cache
```

요청률과 단계 시간은 변경할 수 있다.

```shell
k6 run -e WARM_RATES=10,25,50 -e WARM_STAGE_DURATION=1m scripts/performance/warm-cache.js
```

워밍업 요청률과 시간도 변경할 수 있다.

```shell
k6 run -e WARMUP_RATE=10 -e WARMUP_DURATION=30s scripts/performance/warm-cache.js
```

공용 실행기를 사용할 때는 운영체제의 환경 변수로 설정을 전달한다.

```powershell
$env:WARM_RATES = '10,25,50'
$env:WARM_STAGE_DURATION = '1m'
.\scripts\performance\run.ps1 -Scenario warm-cache
```

```bash
WARM_RATES=10,25,50 WARM_STAGE_DURATION=1m bash scripts/performance/run.sh warm-cache
```

실행 로그와 설정은 다음 위치에 저장된다.

```text
docs/performance/results/YYYY-MM-DD/HHmmss-warm-cache/
```

실행 후 Prometheus 조회 결과는 `prometheus-result.json`, 지표 해설과 판정은
`analysis.md`에 추가한다.

## 확인 항목

- 단계별 redirect p50, p95, p99와 HTTP 실패율
- 실제 요청률과 목표 arrival rate, dropped iteration
- `srrrg.redirect.write`의 click 및 redirect 지연
- HikariCP active, pending과 timeout
- PostgreSQL connection, transaction과 I/O
- Pod CPU, throttling, 메모리와 Tomcat busy thread

각 단계는 Grafana와 Prometheus에서 실행 시각을 기준으로 구분한다. 첫 실행이 안정적이면
동일한 링크 수와 단계 시간을 유지하면서 요청률을 점진적으로 높여 최초 기준 초과 지점을
찾는다.
