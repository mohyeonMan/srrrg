# Concurrent cold-cache 테스트

## 목적

같은 미검증 URL에 요청이 동시에 들어올 때 중복 위험 검사 횟수와 지연을 확인한다.

## 환경 설정

```yaml
- name: SRRRG_URL_RISK_FIXED_SAFE_DELAY
  value: 100ms
- name: SRRRG_URL_RISK_FIXED_SAFE_CACHE_DURATION
  value: 1s
```

100ms delay로 외부 검사 대기 중 겹치는 요청을 만들고, 1초 cache duration으로 첫 검사
완료 후 도착한 요청은 결과를 재사용할 수 있게 한다.

## 실행

스크립트는 10 RPS로 30초 워밍업하고 15초 동안 요청을 멈춘다. 이 공백 동안 대상
URL의 setup 캐시와 워밍업 요청이 Prometheus에 반영되고 대상 URL의 캐시도 만료된다.
이후 모든 VU가 동일한 대상 링크를 한 번씩 요청한다.

```powershell
$env:CONCURRENT_VUS = '10'
.\scripts\performance\run.ps1 -Scenario concurrent-cold-cache
```

10·25·50 VU는 결과 구간을 분리하기 위해 각각 실행한다.

## 판정

- concurrent redirect 증가량을 확인한다.
- 같은 구간의 `miss_stale`, cache hit와 fixed-safe 검사 증가량을 비교한다.
- `fixed-safe 검사 증가량 / 고유 대상 URL 1개`를 중복 검사 배수로 기록한다.
- 서버 p95·p99, redirect write 지연, Hikari pending·timeout과 DB deadlock을 확인한다.

Prometheus scrape 사이에 끝나는 순간 gauge는 최고치를 놓칠 수 있으므로, 중복 검사
판정은 gauge가 아니라 counter 증가량을 기준으로 한다.
