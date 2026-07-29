# srrrg 성능 테스트 실행 체크리스트

## 1. 목적

이 문서는 srrrg 성능 테스트를 준비하고 실행할 때 사용할 작업 순서를 정의한다.
메트릭과 테스트 시나리오의 상세 설계는
[`k6-performance-test-plan.md`](./k6-performance-test-plan.md)를 참고한다.

각 단계는 앞 단계의 수집 및 검증이 완료된 뒤 진행한다.

## 2. 애플리케이션 메트릭 구현

- [x] `srrrg.redirect`로 리다이렉트 전체 처리 시간과 outcome을 기록한다.
- [x] `srrrg.redirect.write`로 click 및 redirect 트랜잭션 전체 시간을 기록한다.
- [x] `srrrg.link.create`로 링크 생성 전체 처리 시간과 outcome을 기록한다.
- [x] `srrrg.url.risk.cache`로 `hit`, `miss_absent`, `miss_stale`를 기록한다.
- [x] `srrrg.url.risk.check`로 provider별 검사 시간과 outcome을 기록한다.
- [x] 링크 코드 충돌과 생성 재시도 소진 횟수를 Counter로 기록한다.
- [x] 정상, 업무 오류, 예외 경로마다 Timer 또는 Counter가 정확히 한 번 기록되는지 테스트한다.
- [x] 단축 코드, 원본 URL, IP 주소와 같은 요청별 값을 metric label에 사용하지 않는다.

## 3. Histogram 설정

- [x] `http.server.requests`의 percentile histogram을 활성화한다.
- [x] 모든 srrrg 커스텀 Timer의 percentile histogram을 활성화한다.
- [x] Timer별 `minimum-expected-value`와 `maximum-expected-value`를 설정한다.
- [x] 100ms, 250ms, 500ms 등 판정에 사용할 SLO bucket을 설정한다.
- [x] 각 Pod가 p50, p95, p99를 직접 계산하는 client-side `percentiles` 설정은 사용하지 않는다.
- [x] Prometheus의 `histogram_quantile()`로 전체 Pod를 합산한 p50, p95, p99를 계산한다.
- [x] `/actuator/prometheus`에 각 Timer의 `_bucket`, `_count`, `_sum`이 노출되는지 확인한다.

## 4. JDBC connection pool 설정 고정

- [x] HikariCP `maximum-pool-size`를 명시한다.
- [x] HikariCP `minimum-idle`을 명시한다.
- [x] `connection-timeout`과 `validation-timeout`을 명시한다.
- [x] `replica 수 × maximum-pool-size`로 애플리케이션의 최대 DB connection 수를 계산한다.
- [x] exporter, 관리자 접속과 다른 애플리케이션을 고려해 PostgreSQL connection에 20~30% 여유를 둔다.
- [x] 성능 테스트 결과에 실제 HikariCP 설정값을 기록한다.

초기값은 replica당 `maximum-pool-size=10`, `minimum-idle=10`,
`connection-timeout=2s`로 시작하고 테스트 결과에 따라 조정한다.
현재 dev 1개와 prod 1개 replica의 애플리케이션 connection 상한은 합계 20개다.
PostgreSQL `max_connections=100`에서 exporter와 관리자 접속을 포함해 30% 이상의
여유를 확보한다.

## 5. Prometheus 수집 주기 설정

- [x] dev 성능 테스트 환경의 애플리케이션 scrape interval을 5초로 설정한다.
- [x] 운영 애플리케이션의 scrape interval을 10초로 설정한다.
- [x] postgres_exporter의 scrape interval을 10초로 설정한다.
- [x] 애플리케이션 scrape timeout을 3초로 설정한다.
- [x] postgres_exporter scrape timeout을 5초로 설정한다.
- [x] Prometheus Targets 화면에서 모든 Pod와 exporter가 정상 수집되는지 확인한다.
- [x] 5초 scrape에서는 30초~2분, 10초 scrape에서는 1~5분의 PromQL rate 구간을 사용한다.

## 6. PostgreSQL 통계 구성

- [x] PostgreSQL에 `shared_preload_libraries=pg_stat_statements`를 설정한다.
- [x] `compute_query_id=on`을 설정한다.
- [x] `track_io_timing=on`을 설정한다.
- [x] `pg_stat_statements.max`와 `pg_stat_statements.track`을 명시한다.
- [x] 설정 변경 후 PostgreSQL을 재시작한다.
- [x] `postgres`, `srrrg-dev`, `srrrg-prod` DB에 `pg_stat_statements` extension을 생성한다.
- [x] 기존 PVC에서는 `/docker-entrypoint-initdb.d`가 재실행되지 않는 점을 고려해 일회성 Job 또는 관리자 작업으로 extension을 생성한다.
- [ ] 테스트 전후 `pg_stat_statements` snapshot을 저장하고 차이를 비교한다.
- [ ] 공유 인스턴스 전체 통계를 무심코 초기화하지 않도록 `pg_stat_statements_reset()` 사용을 제한한다.

## 7. postgres_exporter 배포

- [x] exporter 전용 PostgreSQL 사용자를 생성한다.
- [x] exporter 사용자에게 `pg_monitor` 역할을 부여한다.
- [x] 별도 비밀번호를 사용하면 Kubernetes Secret 또는 SealedSecret으로 관리하고, 로컬 socket 인증이면 네트워크 노출 없이 전용 역할을 사용한다.
- [x] `postgres` namespace에 exporter Deployment 또는 PostgreSQL sidecar를 추가한다.
- [x] exporter의 9187 포트를 노출하는 Service를 추가한다.
- [x] exporter ServiceMonitor를 추가한다.
- [x] connection, transaction, lock, table, cache와 I/O 관련 metric이 수집되는지 확인한다.
- [x] 초기에는 `pg_stat_statements` exporter collector와 SQL 전문 label을 사용하지 않는다.
- [ ] SQL별 시계열이 필요해지면 query 수를 제한해 `stat_statements` collector를 추가한다.

## 8. Grafana 성능 테스트 대시보드 생성 및 확인

- [x] 전체 RPS와 endpoint별 RPS 패널을 만든다.
- [x] 전체 HTTP 오류율과 상태 코드별 요청 수 패널을 만든다.
- [x] 전체 Pod를 합산한 HTTP p50, p95, p99 패널을 만든다.
- [x] `srrrg.redirect`, `srrrg.redirect.write`, `srrrg.link.create`의 p95와 p99 패널을 만든다.
- [x] URL 위험 검증 cache hit ratio와 provider별 검사 수 및 지연 패널을 만든다.
- [x] Pod별 RPS와 p95를 나란히 비교하는 패널을 만든다.
- [x] Pod별 CPU, CPU throttling, 메모리, restart 패널을 만든다.
- [x] JVM heap, GC pause와 Tomcat busy thread 패널을 만든다.
- [x] HikariCP active, idle, pending, max와 timeout 패널을 만든다.
- [x] PostgreSQL connection, transaction, lock, deadlock, cache와 I/O 패널을 만든다.
- [ ] 대시보드의 전체 집계 값이 Pod별 값의 합계와 일치하는지 낮은 부하로 확인한다.
- [x] 테스트 구간을 Grafana annotation 또는 실행 시각으로 식별할 수 있게 한다.
- [x] 대시보드 JSON 또는 provisioning 파일을 버전 관리한다.

전체 서비스 지연은 모든 Pod의 histogram bucket을 합산한 뒤 계산한다.
병목과 요청 편중을 진단할 수 있도록 Pod별 지연도 별도 패널로 유지한다.

## 9. k6 기본 스크립트 작성

- [x] 테스트 데이터 생성 및 정리 도구를 작성한다.
- [ ] Baseline 시나리오를 작성한다.
- [x] Warm-cache redirect 시나리오를 작성한다.
- [ ] Cold-cache redirect 시나리오를 작성한다.
- [x] 시나리오별 arrival rate, 단계 시간과 테스트 데이터 수를 명시한다.
- [x] p95, p99와 예상하지 않은 오류율에 대한 k6 threshold를 정의한다.
- [x] 실행 일시, 애플리케이션 commit SHA, 시나리오 설정과 결과를 함께 저장한다.
- [ ] 인프라 commit SHA, replica 수, Pod resource limit과 HikariCP pool size를 결과에 기록한다.
- [x] secret key와 인증 header를 로그 및 결과 파일에 기록하지 않는다.

## 10. 기본 검증

- 실행 방법과 예상 메트릭 증가량은
  [`smoke-test.md`](./smoke-test.md)를 참고한다.
- [x] URL 위험 검사 provider를 `fixed-safe`로 설정한다.
- [ ] 순수 애플리케이션 및 DB 성능 확인은 delay `0ms`로 시작한다.
- [ ] VU 1로 Baseline을 실행한다.
- [x] k6 요청 수와 Prometheus HTTP 요청 증가량이 일치하는지 확인한다.
- [x] Warm-cache에서 cache hit가 의도대로 발생하는지 확인한다.
- [ ] Cold-cache에서 cache miss가 의도대로 발생하는지 확인한다.
- [x] Grafana에서 p95, p99, 오류율과 자원 지표가 테스트 시간대에 표시되는지 확인한다.
- [x] HikariCP와 PostgreSQL connection 수가 설정값과 일치하는지 확인한다.

## 11. 부하 테스트 실행

- Warm-cache 단계별 테스트 실행 방법은
  [`warm-cache-test.md`](./warm-cache-test.md)를 참고한다.
- [x] 단일 dev Pod의 Warm-cache redirect를 350 RPS까지 검증한다.
- [ ] Warm-cache redirect의 최대 지속 처리량을 찾는다.
- [ ] Cold-cache redirect를 실행한다.
- [ ] Concurrent cold-cache로 동일 URL의 중복 검사 수준을 확인한다.
- [ ] Cache hit 및 miss 링크 생성을 분리해 실행한다.
- [ ] Mixed workload를 실행한다.
- [ ] Spike 테스트 후 지연과 connection이 정상 상태로 회복되는지 확인한다.
- [ ] Soak 테스트에서 heap, RSS, GC, connection과 DB 크기의 장기 추세를 확인한다.
- [ ] 같은 설정에서 replica 1과 2를 비교한다.

## 12. 결과 판정 및 기록

- [x] 모든 테스트 종료 후 동일한 실행 구간을 Prometheus API로 직접 조회한다.
- [x] Prometheus 조회 원본을 `prometheus-result.json`에 저장한 뒤 판정한다.
- [x] 부하 단계별 RPS, p50, p95, p99와 오류율을 기록한다.
- [x] 관측한 CPU와 메모리, CPU throttling 및 GC pause 여부를 기록한다.
- [x] HikariCP 최대 active 및 pending connection과 timeout 수를 기록한다.
- [x] PostgreSQL connection, commit 처리량, lock wait와 deadlock을 기록한다.
- [x] cache hit ratio와 URL 위험 검사 중복 호출 수를 기록한다.
- [ ] Pod별 요청 분배와 replica 확장 효율을 기록한다.
- [ ] 기준을 초과한 최초 부하 단계와 병목 원인을 기록한다.
- [ ] 개선 전후 테스트는 동일한 설정과 데이터로 다시 실행한다.

결과 판정의 기준 데이터는 Grafana 화면이 아니라 Prometheus API 조회값이다.
Grafana는 지표 흐름과 이상 시점을 빠르게 찾는 보조 수단으로만 사용한다.
Prometheus 조회와 `analysis.md` 작성이 끝나기 전에는 해당 실행의 분석 상태를
`complete`로 변경하지 않는다.

지연 변곡점이나 DB 병목이 관측되면 해당 실행에 한해 `pg_stat_statements`의
상위 SQL과 PostgreSQL I/O를 추가로 확인한다.
