# 링크 생성 성능 테스트

## 목적

링크 생성의 URL 위험 검증 cache hit와 miss를 분리해 외부 검사, BCrypt, DB insert
비용을 확인한다.

## 실행 조건

- 단일 Pod에서 실행한다.
- `fixed-safe.delay=100ms`, `fixed-safe.cache-duration=15m`를 사용한다.
- 링크 생성 전용 워밍업은 1 RPS로 30초 실행한다.
- 측정 부하는 1, 2, 5 RPS를 각각 30초 실행한다.
- cache hit는 setup에서 한 URL을 캐시에 넣고 같은 URL을 재사용한다.
- cache miss는 요청마다 고유 URL을 사용한다.
- 생성한 링크는 응답의 secret key로 즉시 삭제하며 secret key를 결과에 기록하지 않는다.

링크 생성은 BCrypt와 쓰기 트랜잭션을 포함하므로 redirect 워밍업 10 RPS를 그대로
사용하지 않는다.

## 실행

```powershell
$env:LINK_CREATE_CACHE_MODE = 'hit'
$env:LINK_CREATE_RATES = '1'
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\performance\run.ps1 -Scenario link-create
```

`LINK_CREATE_CACHE_MODE`를 `miss`로 바꿔 miss 시나리오를 실행한다. 각 RPS는 별도
실행해 앞 단계의 실패가 다음 단계 결과에 섞이지 않게 한다.

## 판정

- 링크 생성 HTTP 실패와 dropped iteration은 0이어야 한다.
- 링크 생성 p95는 300ms 미만, p99는 500ms 미만이어야 한다.
- cache hit 실행은 setup의 최초 miss를 제외한 생성이 hit여야 한다.
- cache miss 실행은 생성 수, `miss_absent` 수와 fixed-safe 검사 수가 일치해야 한다.
- Hikari pending/timeout과 PostgreSQL deadlock이 없어야 한다.

각 실행 후 metadata의 정확한 시작·종료 구간을 기준으로 Prometheus HTTP API를 직접
조회한다. `srrrg.link.create`, URL 위험 캐시/검사, 앱·PostgreSQL CPU와 Hikari
connection을 `prometheus-result.json`에 기록한 뒤 결과를 판정한다.
