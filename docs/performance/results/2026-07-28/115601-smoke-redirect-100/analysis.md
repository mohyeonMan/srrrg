# Smoke test 분석: redirect 100회

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-28 11:56:01 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 시나리오 | VU 1, 링크 생성 1회, 관리 조회 1회, warm-cache redirect 100회, 삭제 1회 |
| 애플리케이션 commit | `353b8234783ac6b15aa17e099d85e0e9d06adc54` |
| 인프라 commit | `f3ee1fd73298c6cf50f3ef6d53d873758f2d071f` |
| 스크립트 상태 | commit 이후 작업 트리에 추가된 `smoke.js` 사용 |
| URL 위험 검사 | `fixed-safe`, delay `100ms`, cache duration `5m` |

## k6 결과

| 항목 | 결과 |
|---|---:|
| HTTP 요청 | 103 |
| HTTP 실패 | 0 |
| check | 208 / 208 성공 |
| 평균 지연 | 27.62ms |
| 중앙값 | 14.75ms |
| p90 | 62.94ms |
| p95 | 87.02ms |
| 최대 지연 | 191.38ms |
| 실행 시간 | 약 3.2초 |
| 처리 속도 | 약 32.13 req/s |

k6 지연에는 링크 생성, 관리 조회, redirect와 삭제가 모두 포함된다. 따라서 이 실행의
k6 p95를 warm-cache redirect만의 성능으로 해석하지 않는다.

## Prometheus 실행 전후 비교

| 메트릭 | 실행 전 | 실행 후 | 증가량 | 예상 |
|---|---:|---:|---:|---:|
| `POST /api/links` 201 | 1 | 2 | 1 | 1 |
| `GET /api/links/{code}` 200 | 1 | 2 | 1 | 1 |
| `GET /{code}` 302 | 20 | 120 | 100 | 100 |
| `DELETE /api/links/{code}` 200 | 1 | 2 | 1 | 1 |
| `srrrg.link.create{outcome="created"}` | 1 | 2 | 1 | 1 |
| risk cache `miss_absent` | 1 | 2 | 1 | 1 |
| risk check `fixed_safe/safe` | 1 | 2 | 1 | 1 |
| risk cache `hit` | 20 | 120 | 100 | 100 |
| redirect `redirected` | 20 | 120 | 100 | 100 |
| redirect write `click/success` | 20 | 120 | 100 | 100 |
| redirect write `redirect/success` | 20 | 120 | 100 | 100 |

## 서버 상태

| 항목 | 결과 |
|---|---:|
| Prometheus redirect p50 | 6.66ms |
| Prometheus redirect p95 | 67.11ms |
| Prometheus redirect p99 | 85.00ms |
| click 쓰기 p50 / p95 / p99 | 2.37ms / 15.38ms / 67.11ms |
| redirect 쓰기 p50 / p95 / p99 | 2.27ms / 13.98ms / 41.94ms |
| 링크 생성 p95 | 178.08ms |
| fixed-safe risk check p95 | 111.26ms |
| 최근 5분 HTTP 5xx | 0 |
| HikariCP timeout 누적 | 0 |
| 종료 후 HikariCP active connection | 0 |
| srrrg-dev target | UP |
| srrrg-prod target | UP |
| postgres exporter target | UP |

테스트가 dev scrape interval 5초보다 짧게 끝났기 때문에 순간적인 active connection
최고치는 수집되지 않았을 수 있다.

## 해설

- warm-cache redirect는 서버 p95 67.11ms, p99 85.00ms로 초기 기준인 p95 100ms,
  p99 250ms 미만을 모두 만족했다.
- k6 중앙값 14.75ms와 서버 redirect p50 6.66ms의 차이에는 클라이언트와 서버 사이의
  네트워크 및 HTTP 처리 시간이 포함된다. 서로 다른 분포의 percentile을 직접 빼서
  정확한 네트워크 지연으로 해석하지는 않는다.
- click과 redirect 쓰기의 p50은 각각 약 2.3ms로 낮았다. p95도 약 14~15ms이며
  HikariCP pending과 timeout이 없어 현재 부하에서 connection pool 포화 징후는 없다.
- 링크 생성 p95와 risk check p95는 표본이 각각 소수이므로 안정적인 percentile이 아니다.
  fixed-safe delay 100ms가 설정된 상태에서 risk check가 약 111ms bucket으로 관측된 것은
  설정 의도와 일치한다.
- 1 VU가 요청을 순차 실행했기 때문에 32.13 req/s는 최대 처리량이 아니다. 이 결과는
  메트릭 정확성과 낮은 부하의 정상 동작을 확인하는 baseline smoke 결과로만 사용한다.
- 테스트가 5초 scrape보다 짧아 CPU, Tomcat busy thread와 HikariCP active connection의
  순간 최고치는 판정할 수 없다. 이 지표는 이후 각 부하 단계를 최소 수십 초 유지하는
  arrival-rate 테스트에서 확인한다.

## 판정

성공. k6 요청 수와 애플리케이션·Prometheus Counter 증가량이 모두 일치했다.
warm-cache redirect, URL 위험 검사 캐시, redirect 쓰기 트랜잭션 메트릭이 의도대로
수집됐으며 HTTP 5xx와 connection timeout은 발생하지 않았다.
