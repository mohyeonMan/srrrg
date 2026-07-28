# Smoke test: redirect 100회

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
| Prometheus redirect p95 | 67.11ms |
| 최근 5분 HTTP 5xx | 0 |
| HikariCP timeout 누적 | 0 |
| 종료 후 HikariCP active connection | 0 |
| srrrg-dev target | UP |
| srrrg-prod target | UP |
| postgres exporter target | UP |

테스트가 dev scrape interval 5초보다 짧게 끝났기 때문에 순간적인 active connection
최고치는 수집되지 않았을 수 있다.

## 판정

성공. k6 요청 수와 애플리케이션·Prometheus Counter 증가량이 모두 일치했다.
warm-cache redirect, URL 위험 검사 캐시, redirect 쓰기 트랜잭션 메트릭이 의도대로
수집됐으며 HTTP 5xx와 connection timeout은 발생하지 않았다.
