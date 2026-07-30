# srrrg 성능 메트릭 및 k6 테스트 계획

## 1. 목적

이 문서는 srrrg의 성능 테스트를 수행하기 전에 추가할 애플리케이션 메트릭과 k6 테스트 시나리오를 정의한다.
실제 준비와 실행 순서는
[`performance-test-checklist.md`](./performance-test-checklist.md)를 따른다.

성능 테스트에서는 다음 질문에 답할 수 있어야 한다.

- 단축 URL 생성과 리다이렉트의 처리량 및 지연 시간은 어느 정도인가?
- 부하가 증가할 때 오류율과 지연 시간이 어떻게 변하는가?
- Safe Browsing 캐시가 외부 API 호출을 충분히 줄이고 있는가?
- PostgreSQL connection pool과 Pod 자원이 먼저 포화되지 않는가?
- 애플리케이션 replica를 늘렸을 때 처리량과 가용성이 실제로 개선되는가?
- 지속 부하에서 메모리, connection 또는 이벤트 데이터가 비정상적으로 누적되지 않는가?

## 2. 측정 원칙

메트릭은 latency, traffic, errors, saturation 네 영역을 기준으로 구성한다.

| 영역 | 확인 대상 |
|---|---|
| Latency | 요청 처리 시간, 외부 API 호출 시간, DB connection 획득 시간 |
| Traffic | 초당 요청 수, 생성 수, 리다이렉트 수, Safe Browsing 호출 수 |
| Errors | HTTP 5xx, 업무 오류, 외부 API 오류, DB 기록 실패 |
| Saturation | CPU, 메모리, GC, connection pool 대기, CPU throttling |

메트릭 label에는 다음 값을 사용하지 않는다.

- 단축 코드
- 원본 URL
- IP 주소
- Referer
- User-Agent
- secret key

요청마다 달라질 수 있는 값을 label로 사용하면 Prometheus 시계열 수가 지속적으로 증가한다. 모든 label은 미리 정해진 제한된 값만 가져야 한다.

Timer의 p50, p95와 p99는 각 Pod에서 미리 계산하지 않는다. 각 Pod는 percentile histogram bucket을 노출하고,
Prometheus가 모든 Pod의 bucket을 합산한 뒤 `histogram_quantile()`로 전체 서비스의 percentile을 계산한다.
병목과 부하 편중을 확인하기 위한 Pod별 percentile도 같은 histogram에서 별도로 계산한다.

## 3. 적용 예정 애플리케이션 메트릭

### 3.1 리다이렉트

#### `srrrg.redirect`

`RedirectService.redirect()` 전체 처리 시간을 측정하는 Timer다.

| Label | 값 |
|---|---|
| `outcome` | `redirected`, `blocked`, `not_found`, `gone`, `check_failed`, `error` |

측정 범위에는 다음 작업을 포함한다.

- 링크 조회 및 사용 가능 여부 확인
- Safe Browsing 캐시 조회 및 필요 시 외부 검사
- 결과별 접근 이벤트 기록
- 접근 및 리다이렉트 카운터 갱신

이 메트릭으로 리다이렉트 처리량, p50, p95, p99와 결과별 비율을 확인한다.

### 3.2 링크 생성

#### `srrrg.link.create`

링크 생성 서비스 전체 처리 시간을 측정하는 Timer다.

| Label | 값 |
|---|---|
| `outcome` | `created`, `invalid`, `threat`, `check_failed`, `error` |

측정 범위에는 다음 작업을 포함한다.

- URL 및 만료 시각 검증
- Safe Browsing 검사
- secret key 해시 생성
- 단축 코드 생성
- 링크 저장

이 메트릭으로 링크 생성 처리량과 외부 검사 및 BCrypt 연산을 포함한 전체 지연 시간을 확인한다.

링크 코드 충돌은 내부에서 재시도되므로 링크 생성 outcome과 별도로 측정한다.

#### `srrrg.link.code_generation`

링크 코드 생성 충돌과 재시도 소진을 기록하는 Counter다.

| Label | 값 |
|---|---|
| `outcome` | `collision`, `exhausted` |

### 3.3 URL 위험 검증 캐시

#### `srrrg.url.risk.cache`

URL 검증 캐시 조회 결과를 기록하는 Counter다.

| Label | 값 |
|---|---|
| `result` | `hit`, `miss_absent`, `miss_stale` |

캐시 효율은 다음과 같이 계산한다.

```text
cache hit ratio = hit / (hit + miss_absent + miss_stale)
```

Warm-cache와 Cold-cache 테스트가 의도한 상태로 수행됐는지 확인하고, 데이터 부재와 만료로 인한 miss를 구분하는 기준으로도 사용한다.

### 3.4 URL 위험 검사

#### `srrrg.url.risk.check`

캐시 miss 이후 선택된 URL 위험 검사 구현체의 처리 시간을 측정하는 Timer다.

| Label | 값 |
|---|---|
| `provider` | `google`, `fixed_safe` |
| `outcome` | `safe`, `threat`, `timeout`, `invalid_response`, `not_configured`, `error` |

다음 항목을 확인한다.

- provider별 검사 호출 수
- 검사 p50, p95, p99
- timeout 및 error 비율
- 캐시 miss 수와 검사 호출 수의 관계

```text
check ratio = URL 위험 검사 호출 수 / cache miss 수
```

`google` provider에서는 실제 Safe Browsing 외부 호출 시간을 나타내고, `fixed_safe` provider에서는 설정한 검사 지연 시간을 나타낸다.

### 3.5 리다이렉트 쓰기 트랜잭션

#### `srrrg.redirect.write`

접근 이벤트와 카운터를 저장하는 단일 쓰기 트랜잭션 전체 시간을 측정하는 Timer다.

| Label | 값 |
|---|---|
| `type` | `access` |
| `outcome` | `success`, `error` |

측정 범위에는 이벤트 테이블 insert, 링크 counter update, flush와 transaction commit을 포함한다.
JPA `save()` 호출만 측정하면 실제 SQL 실행과 commit 시간이 빠질 수 있으므로 `TransactionTemplate` 실행 전체를 측정한다.
이 메트릭은 부하 증가 시 DB 쓰기가 리다이렉트 지연의 병목인지 확인하는 데 사용한다.

## 4. 함께 확인할 런타임 메트릭

커스텀 메트릭과 함께 다음 런타임 메트릭을 확인한다.

### 4.1 HTTP

- URI 및 method별 요청 수
- 응답 상태별 요청 수
- 요청 처리 시간의 p50, p95, p99
- 4xx 및 5xx 비율

URI는 실제 단축 코드가 아니라 `/{code}`와 같은 route template으로 집계되어야 한다.

### 4.2 Servlet thread pool

- current thread
- busy thread
- configured maximum thread

HTTP 지연이 증가할 때 요청 처리 thread가 먼저 포화됐는지 확인한다.

### 4.3 JDBC connection pool

- active connection
- idle connection
- pending connection
- connection 획득 시간
- connection timeout 수
- configured maximum connection

정상 부하에서는 pending connection이 지속적으로 발생하지 않아야 하며 connection timeout은 없어야 한다.

성능 테스트에서는 HikariCP 설정을 기본값에 맡기지 않고 명시한다. 초기에는 replica당
`maximum-pool-size=10`, `minimum-idle=10`, `connection-timeout=2s`로 시작한다.
전체 connection 상한은 `replica 수 × maximum-pool-size`로 계산하고 PostgreSQL 관리자 및 exporter connection을 위한 여유를 둔다.

### 4.4 JVM 및 컨테이너

- JVM heap 사용량
- Process RSS 메모리
- GC pause 시간 및 횟수
- Process 및 컨테이너 CPU 사용량
- CPU throttling
- Pod restart 및 OOMKill
- Pod별 네트워크 송수신량

### 4.5 PostgreSQL

- 현재 및 최대 connection 수
- transaction 처리량
- lock wait와 deadlock

DB 전체 상태의 시계열은 postgres_exporter로 수집한다.
`pg_stat_statements`는 `shared_preload_libraries`, `compute_query_id`와 `track_io_timing`을 명시하고 PostgreSQL을 재시작한 뒤
각 대상 DB에 extension을 생성한다. 테스트마다 공유 통계를 초기화하기보다 테스트 전후 snapshot 차이를 비교한다.
상위 SQL과 디스크 I/O는 지연 변곡점이나 DB 병목이 관측된 실행에서만 추가로 확인한다.

### 4.6 Histogram과 수집 주기

다음 Timer에는 percentile histogram, 예상 최소·최대값과 판정용 SLO bucket을 설정한다.

- `http.server.requests`
- `srrrg.redirect`
- `srrrg.redirect.write`
- `srrrg.link.create`
- `srrrg.url.risk.check`

각 Pod가 percentile을 직접 계산해 노출하는 client-side `percentiles` 설정은 사용하지 않는다.
Prometheus의 집계 가능한 histogram을 사용하며 `/actuator/prometheus`에서 `_bucket`, `_count`, `_sum`이 노출되는지 확인한다.

권장 scrape 설정은 다음과 같다.

| 대상 | interval | timeout |
|---|---:|---:|
| dev 성능 테스트 애플리케이션 | 5초 | 3초 |
| 운영 애플리케이션 | 10초 | 3초 |
| postgres_exporter | 10초 | 5초 |

30초 주기는 짧은 spike, HikariCP pending connection과 thread pool 포화를 놓칠 수 있으므로 성능 테스트에는 사용하지 않는다.
5초 scrape에서는 30초~2분, 10초 scrape에서는 1~5분의 PromQL rate 구간을 사용한다.

## 5. 메트릭 확인 방법

### 5.1 테스트 중

k6 결과와 서버 메트릭의 시간을 맞춰 다음 관계를 확인한다.

| k6 관측값 | 함께 확인할 서버 메트릭 |
|---|---|
| p95, p99 증가 | 리다이렉트·쓰기·생성 Timer, URL 위험 검사, DB connection 획득 시간 |
| 요청 실패 증가 | HTTP status, 업무 outcome, 외부 API 오류, DB 저장 오류 |
| 처리량 정체 | Ingress 오류, CPU throttling, connection pending, PostgreSQL transaction 처리량 |
| replica 간 처리량 차이 | Pod별 요청 수, CPU, readiness |
| 시간에 따른 지연 증가 | heap, GC pause, connection 수 |

### 5.2 테스트 종료 후

모든 테스트는 종료 직후 실행 시작·종료 시각과 부하 단계 구간을 기준으로 Prometheus
API를 직접 조회한다. Grafana 패널에 표시된 값을 결과 수치로 옮기지 않는다.
Prometheus 원시 조회 결과를 실행 디렉터리의 `prometheus-result.json`에 저장하고,
그 값을 근거로 `analysis.md`를 작성한다.

평균값만 사용하지 않고 다음을 함께 기록한다.

- p50, p95, p99
- 초당 처리량
- 최대 VU
- 요청 및 check 실패율
- 결과별 요청 수
- 관측 CPU와 메모리
- 최대 active/pending DB connection
- GC pause
- URL 위험 검증 cache hit ratio
- provider별 URL 위험 검사 수
- Pod별 요청 분배

지연 변곡점이나 DB 병목이 관측된 경우에만 PostgreSQL 상위 SQL과 I/O를 추가로 확인한다.

### 5.3 Grafana 대시보드

성능 테스트 전에 다음 패널을 포함한 전용 대시보드를 만든다.

- 전체 및 endpoint별 RPS, 오류율과 p50, p95, p99
- srrrg 커스텀 Timer의 p95와 p99
- URL 위험 검증 cache hit ratio와 provider별 검사 지연
- Pod별 RPS, p95, CPU, CPU throttling, 메모리와 restart
- JVM heap, GC pause와 Servlet busy thread
- HikariCP active, idle, pending, max와 timeout
- PostgreSQL connection, transaction, lock, deadlock, cache와 I/O

전체 서비스 percentile은 모든 Pod의 histogram bucket을 합산해 계산한다.
Pod별 패널은 요청 편중과 특정 Pod의 이상을 진단하기 위해 함께 유지한다.
낮은 부하의 기본 검증에서 Grafana 전체 요청 수가 Pod별 요청 수 합계 및 k6 요청 수와 일치하는지 확인한 뒤 본 테스트를 실행한다.
테스트 구간은 annotation 또는 실행 시각으로 식별하고 대시보드 JSON이나 provisioning 파일을 버전 관리한다.
Grafana는 전체 흐름과 이상 시점 탐색에 사용하며 최종 수치와 판정은 동일 PromQL을
Prometheus API에 직접 실행한 결과를 사용한다.

## 6. 성능 테스트용 URL 위험 검사 설정

k6 성능 테스트에서는 실제 Google Safe Browsing API를 호출하지 않는다.

성능 테스트 환경에서는 URL 위험 검사 provider를 `fixed-safe`로 변경한다. 이 provider는 외부 요청 없이 항상 `SAFE` 결과와 cache duration을 반환한다.

```text
SRRRG_URL_RISK_PROVIDER=fixed-safe
SRRRG_URL_RISK_FIXED_SAFE_DELAY=100ms
SRRRG_URL_RISK_FIXED_SAFE_CACHE_DURATION=5m
```

delay는 외부 검사로 인해 요청 처리가 대기하는 시간을 재현한다.

| 테스트 목적 | delay |
|---|---:|
| srrrg와 DB의 순수 처리량 | `0ms` |
| 일반적인 외부 검사 대기 모사 | `100ms` |
| 느린 외부 검사 상황 | `500ms` |

`fixed-safe`도 실제 구현과 동일하게 `UrlRiskAssessment`를 반환하므로 DB 캐시의 저장, 만료와 hit/miss 흐름은 그대로 수행된다. 같은 미검증 URL의 동시 요청과 replica별 중복 검사도 `srrrg.url.risk.check` 메트릭으로 측정할 수 있다.

실제 Google API 연동은 k6 부하 테스트와 분리한다. 배포 전 통합 확인에서 소수의 고정된 안전·위협 URL만 요청하며 처리량이나 동시성 측정에는 사용하지 않는다.

## 7. k6 테스트 시나리오

각 시나리오는 별도 실행한다. Warm-cache와 Cold-cache 요청을 하나의 결과로 합치면 애플리케이션 지연과 외부 API 지연을 구분할 수 없다.

### 7.1 Baseline

목적은 낮은 부하에서 정상적인 처리 시간과 자원 사용량을 확보하는 것이다.

- replica 수: 1
- VU: 1
- 대상: 링크 생성, 캐시된 리다이렉트, 관리 조회
- 실행 시간: 각 요청을 충분히 반복할 수 있는 짧은 고정 시간

이 결과를 이후 부하 테스트의 기준값으로 사용한다.

### 7.2 Warm-cache redirect

목적은 URL 위험 검사를 제외한 리다이렉트 경로의 처리량과 DB 쓰기 성능을 확인하는 것이다.

- URL 위험 검증 캐시가 유효한 링크를 사용한다.
- 여러 단축 코드를 준비해 특정 row에만 부하가 집중되지 않게 한다.
- 일정한 arrival rate를 단계적으로 증가시킨다.
- 각 단계에서 p95와 p99가 급격히 증가하는 지점을 찾는다.

주요 확인 항목:

- `srrrg.redirect`의 p95와 p99
- 초당 리다이렉트 수
- `srrrg.redirect.write`의 access 트랜잭션 시간
- active 및 pending DB connection
- PostgreSQL I/O
- Pod CPU와 CPU throttling

### 7.3 Cold-cache redirect

목적은 검증 캐시가 없는 URL에서 URL 위험 검사가 전체 리다이렉트 지연에 미치는 영향을 확인하는 것이다.

- 테스트마다 캐시 miss가 보장되는 URL 집합을 사용한다.
- `fixed-safe` provider의 delay로 검사 시간을 통제한다.
- cache miss 수와 URL 위험 검사 수를 비교한다.

주요 확인 항목:

- `srrrg.url.risk.cache{result=~"miss_absent|miss_stale"}`
- `srrrg.url.risk.check{provider="fixed_safe"}`의 호출 수와 지연
- `srrrg.redirect`에서 URL 위험 검사가 차지하는 지연

### 7.4 Concurrent cold-cache

목적은 같은 미검증 URL에 요청이 동시에 들어올 때 중복 외부 호출과 DB 경합 정도를 측정하는 것이다.

- 같은 원본 URL을 참조하는 테스트 링크를 준비한다.
- 해당 URL의 캐시가 없는 상태에서 여러 VU가 동시에 시작한다.
- replica 1과 replica 2에서 각각 실행한다.

주요 확인 항목:

- cache miss 수
- URL 위험 검사 수
- URL 위험 검사 수 / 고유 cold URL 수로 계산한 중복 검사 배수
- replica 수에 따른 외부 호출 증가량
- URL 검증 캐시 저장 오류와 DB lock wait
- 요청별 p95와 p99

이 시나리오는 현재 구현의 중복 호출 수준을 측정하기 위한 것이며 특정 개선 방식을 전제로 하지 않는다.

### 7.5 Link creation

목적은 링크 생성 과정의 외부 호출, BCrypt와 DB insert 비용을 측정하는 것이다.

- 유효한 URL을 충분히 준비한다.
- 캐시 hit 생성과 cache miss 생성을 별도 실행한다.
- 생성된 링크는 테스트 식별자로 구분하고, secret key는 결과에 남기지 않고 테스트 프로세스 안에서만 정리에 사용한다.

주요 확인 항목:

- `srrrg.link.create`의 p95와 p99
- `created`, `check_failed`, `error` 비율
- 링크 코드 생성 collision 및 exhausted 수
- URL 위험 검증 cache hit ratio
- URL 위험 검사 수
- CPU 사용량
- DB connection과 insert 지연

### 7.6 Mixed workload

목적은 실제 사용 패턴에 가까운 부하에서 읽기와 쓰기가 서로 미치는 영향을 확인하는 것이다.

초기 비율은 다음과 같이 시작하고 실제 사용량에 따라 조정한다.

| 요청 | 비율 |
|---|---:|
| 리다이렉트 | 90% |
| 링크 생성 | 5% |
| 관리 조회 및 변경 | 5% |

주요 확인 항목:

- endpoint별 p95와 p99
- 전체 및 endpoint별 실패율
- DB connection pool
- PostgreSQL I/O와 lock wait
- 리다이렉트 쓰기 트랜잭션 지연
- Pod별 요청 분배

### 7.7 Spike

목적은 순간적인 트래픽 증가 이후 서비스가 정상 상태로 복귀하는지 확인하는 것이다.

- 평상시 요청률을 일정 시간 유지한다.
- 짧은 시간 동안 요청률을 급격히 증가시킨다.
- 다시 평상시 요청률로 낮춘다.

주요 확인 항목:

- spike 구간의 p95, p99와 실패율
- connection pending 및 timeout
- CPU throttling
- spike 종료 후 지연 시간 회복 여부
- Pod restart와 readiness 변화

### 7.8 Soak

목적은 장시간 부하에서 누수와 누적 문제를 확인하는 것이다.

- 예상 운영 부하 또는 그보다 약간 높은 요청률을 유지한다.
- 최소 30분 이상 실행하고 필요하면 수 시간까지 확장한다.

주요 확인 항목:

- heap과 RSS의 지속 증가 여부
- GC pause 변화
- active 및 idle connection 추세
- 이벤트 테이블과 디스크 사용량 증가
- 시간 경과에 따른 p95와 p99 변화
- 오류율 증가 여부

### 7.9 Replica 1 대 2 비교

목적은 replica 증가가 실제 처리량과 지연 시간 개선으로 이어지는지 확인하는 것이다.

- 같은 이미지, 설정, 테스트 데이터와 k6 부하를 사용한다.
- replica 수만 1과 2로 변경한다.
- Warm-cache redirect와 Mixed workload를 각각 실행한다.

다음 값을 비교한다.

```text
scaling efficiency = replica 2의 최대 처리량 / replica 1의 최대 처리량
```

최대 처리량은 동일한 p95와 오류율 기준을 만족하는 구간에서 비교한다.

함께 확인할 항목:

- replica별 요청 수
- replica별 CPU와 메모리
- 전체 DB connection 수
- PostgreSQL CPU 및 I/O 증가량
- provider별 URL 위험 검사 수

## 8. 초기 판정 기준

다음 값은 첫 성능 테스트를 위한 초기 기준이다. Baseline 결과와 실제 사용자 요구사항을 확인한 뒤 조정한다.

| 항목 | 초기 기준 |
|---|---|
| Warm-cache redirect p95 | 100ms 미만 |
| Warm-cache redirect p99 | 250ms 미만 |
| 예상하지 않은 HTTP 5xx | 전체 요청의 0.1% 미만 |
| k6 check 실패 | 예상한 업무 오류를 제외하고 1% 미만 |
| DB connection timeout | 0건 |
| DB pending connection | 지속적으로 발생하지 않을 것 |
| Pod restart 및 OOMKill | 0건 |
| 메모리 | Soak 테스트 중 지속적으로 증가하지 않을 것 |
| CPU throttling | 지연 증가의 주된 원인이 되지 않을 것 |
| replica별 요청 분배 | 충분한 시간 동안 한 Pod에 과도하게 편중되지 않을 것 |
| replica 2 처리량 | 동일한 p95 기준에서 replica 1의 1.5배 이상을 목표로 함 |

Cold-cache와 링크 생성 지연은 URL 위험 검사 시간에 크게 영향을 받는다. 따라서 전체 시간뿐 아니라 `srrrg.url.risk.check` 시간을 제외한 애플리케이션 자체 처리 시간도 함께 판단한다.

## 9. 테스트 결과 기록

각 실행 결과에는 다음 정보를 남긴다.

- 실행 일시와 애플리케이션·인프라 commit SHA
- 배포 환경, replica 수와 Pod resource limit
- HikariCP maximum pool size
- URL 위험 검사 provider, delay와 cache duration
- k6 시나리오, VU 또는 arrival rate 단계와 실행 시간
- p50, p95, p99와 처리량
- HTTP 및 업무 오류율
- 관측 CPU, 메모리와 DB connection
- URL 위험 검증 cache hit ratio와 provider별 검사 수
- 사용한 Grafana 대시보드 버전 또는 링크
- 이전 실행 대비 변화와 결론

테스트 결과는 평균값 하나로 합치지 않고 시나리오와 부하 단계별로 구분해 기록한다.
secret key와 인증 header는 로그나 결과 파일에 저장하지 않는다.
