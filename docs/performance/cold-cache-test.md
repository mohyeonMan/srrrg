# Cold-cache redirect 부하 테스트

## 목적

URL 위험 검사 캐시가 만료된 redirect 경로에서 위험 검사와 캐시 저장을 포함한
애플리케이션·DB 처리 비용을 확인한다.

## 테스트 환경 설정

순수 애플리케이션·DB 비용을 측정하는 첫 테스트에서는 dev 환경을 다음과 같이 설정한다.

```yaml
- name: SRRRG_URL_RISK_FIXED_SAFE_DELAY
  value: 0ms
- name: SRRRG_URL_RISK_FIXED_SAFE_CACHE_DURATION
  value: 1ms
```

cache duration은 애플리케이션 검증 규칙상 양수여야 하므로 `0ms`를 사용하지 않는다.
20개 링크를 순환하는 요청 간격보다 `1ms`가 충분히 짧아 각 redirect에서
`miss_stale`이 발생한다.

## 기본 실행

기본 calibration은 기존과 같은 10 RPS 30초 워밍업 후 5·10·20 RPS를 각각
30초 유지한다. cache duration이 1ms이므로 워밍업도 앱의 완전 콜드스타트만
벗어나게 하며 본 측정 캐시를 warm 상태로 만들지 않는다.

```powershell
.\scripts\performance\run.ps1 -Scenario cold-cache
```

요청률과 단계 시간은 환경 변수로 변경할 수 있다.

```powershell
$env:COLD_RATES = '5,10,20'
$env:COLD_STAGE_DURATION = '30s'
$env:COLD_RAMP_DURATION = '5s'
.\scripts\performance\run.ps1 -Scenario cold-cache
```

## 판정

실행 후 같은 시간 구간의 Prometheus API를 직접 조회한다.

- redirect 수와 `miss_stale` 수가 일치해야 한다.
- fixed-safe 검사 수는 setup의 `miss_absent`와 전체 redirect miss의 합이어야 한다.
- cache hit, 예상하지 않은 HTTP 오류와 dropped iteration은 0이어야 한다.
- 단계별 redirect p95·p99, Hikari pending·timeout과 PostgreSQL 상태를 확인한다.

`analysis.md`와 `prometheus-result.json` 작성 전에는 실행 분석을 완료로 판정하지 않는다.
