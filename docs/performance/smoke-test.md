# srrrg 성능 메트릭 smoke test

## 목적

이 테스트는 본격적인 부하 테스트 전에 애플리케이션 요청과 Prometheus 메트릭이
의도한 대로 연결되는지 확인한다. 처리량 한계나 성능 기준을 판정하는 테스트가 아니다.

실행 코드는 [`../../scripts/performance/smoke.js`](../../scripts/performance/smoke.js)에 둔다.
성능 테스트 계획과 판정 기준은 이 디렉터리에서 문서로 관리하고, 반복 실행하는 코드는
`scripts/performance`에서 관리한다.

## 수행 흐름

스크립트는 VU 1에서 다음 작업을 수행한다.

1. 실행마다 고유한 원본 URL로 링크를 하나 생성한다.
2. secret key로 링크 관리 정보를 조회한다.
3. 생성된 단축 URL을 기본 20회 호출하되 외부 URL로 redirect를 따라가지 않는다.
4. 생성한 테스트 링크를 soft delete한다.

생성된 테스트 링크의 code, short URL과 secret key는 정리나 수동 확인에 사용할 수 있도록
k6 출력에 기록한다. 링크는 삭제되지만 링크 및 이벤트 데이터와 URL 위험 검사 캐시는
데이터베이스에 남을 수 있다.

## 실행

k6가 설치된 환경에서 저장소 루트를 기준으로 실행한다.

```shell
k6 run scripts/performance/smoke.js
```

실행 로그와 메타데이터를 자동 저장하려면 운영체제에 맞는 래퍼를 사용한다.

Windows PowerShell:

```powershell
.\scripts\performance\run-smoke.ps1 -RedirectRequests 100
```

macOS 또는 Linux:

```bash
bash scripts/performance/run-smoke.sh --redirect-requests 100
```

결과는 다음 규칙으로 저장한다.

```text
docs/performance/results/YYYY-MM-DD/HHmmss-smoke-redirect-N/
```

두 래퍼는 동일하게 `k6-output.log`와 `metadata.json`을 자동 생성한다.
Prometheus 실행 전후 비교와
판정은 같은 디렉터리에 `prometheus-result.json`과 `summary.md`로 추가한다.

기본 대상은 dev 환경인 `https://jhhomehub.gonetis.com/srrrg-dev`다.
대상과 redirect 요청 수는 환경 변수로 변경할 수 있다.

```shell
k6 run -e BASE_URL=https://jhhomehub.gonetis.com/srrrg-dev -e SMOKE_REDIRECT_REQUESTS=100 scripts/performance/smoke.js
```

원본 URL을 바꿔야 한다면 `SMOKE_ORIGINAL_URL`을 HTTP 또는 HTTPS URL로 지정한다.
스크립트가 cache miss를 구분할 수 있도록 실행 식별 query parameter를 자동으로 붙인다.

## 성공 조건

k6 결과에서 다음 조건을 만족해야 한다.

- 모든 check가 성공한다.
- `http_req_failed`가 0이다.
- 링크 생성은 201, 관리 조회와 삭제는 200을 반환한다.
- redirect 요청은 모두 302를 반환하고 `Location`이 생성 요청의 원본 URL과 같다.

기본 요청 수 20회 기준으로 애플리케이션에는 총 23개 요청이 발생한다.

| 요청 | 예상 증가량 |
|---|---:|
| `POST /api/links` | 1 |
| `GET /api/links/{code}` | 1 |
| `GET /{code}` | 20 |
| `DELETE /api/links/{code}` | 1 |

## Grafana와 Prometheus 확인

마지막 요청 후 dev scrape interval의 두세 배인 10~15초를 기다린 뒤 테스트 실행 시간대를
조회한다. actuator scrape 요청을 제외하고 다음 증가량을 확인한다.

| 메트릭 | 기본 예상 증가량 |
|---|---:|
| `srrrg.link.create{outcome="created"}` | 1 |
| `srrrg.url.risk.cache{result="miss_absent"}` | 1 이상 |
| `srrrg.url.risk.check{provider="fixed_safe",outcome="safe"}` | 1 이상 |
| `srrrg.url.risk.cache{result="hit"}` | 20 이상 |
| `srrrg.redirect{outcome="redirected"}` | 20 |
| `srrrg.redirect.write{type="click",outcome="success"}` | 20 |
| `srrrg.redirect.write{type="redirect",outcome="success"}` | 20 |

cache 관련 값은 동일한 URL이 이미 검증됐거나 구현 내부에서 추가 조회가 발생하면 예상보다
커질 수 있다. 이 테스트에서는 정확한 전체 Counter 값보다 실행 전후 증가량과 요청 흐름의
일관성을 확인한다.

대시보드에서는 다음 관계도 확인한다.

- 전체 endpoint 요청 증가량과 Pod별 증가량의 합계가 일치한다.
- redirect p95와 p99가 테스트 시간대에 계산된다.
- HikariCP active connection과 PostgreSQL connection이 요청 시간대에 관측된다.
- 예상하지 않은 HTTP 5xx, HikariCP timeout, Pod restart가 발생하지 않는다.
