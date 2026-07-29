# Warm-cache 200 RPS 재검증 분석

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-29 11:10:13 ~ 11:13:00 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 워밍업 | 10 RPS, 30초, 300 redirect |
| 본 측정 | 10초 ramp 후 200 RPS, 2분 유지 |
| 테스트 링크 | 20개 |
| pre-allocated / max VU | 40 / 200 |
| 애플리케이션 commit | `e7484af47a90658417d8c35b40f0a4ec28175158` |
| 인프라 commit | `40757b82baa97a336e897c12833c20697130a50b` |
| Pod | dev 1 replica, CPU 500m / memory 1Gi limit |
| HikariCP | maximum 10, minimum idle 10, connection timeout 2초 |
| URL 위험 검사 | `fixed-safe`, delay `100ms`, cache duration `15m` |
| k6 종료 코드 | 0 |

애플리케이션 작업 트리는 테스트 스크립트와 문서 변경으로 dirty 상태였고, 인프라
작업 트리는 clean 상태였다.

## k6 결과

| 항목 | 결과 |
|---|---:|
| 워밍업 redirect | 300 |
| 본 측정 redirect | 25,049 |
| 전체 redirect | 25,349 |
| 전체 HTTP 요청 | 25,389 |
| check | 25,449 / 25,449 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| 본 측정 p95 | 14.85ms |
| 본 측정 p99 | 24.14ms |
| 본 측정 최대 | 287.57ms |
| 최대 활성 VU | 6 |

p95 100ms, p99 250ms, HTTP 실패율 0.1% 미만, dropped iteration 최종 10건
미만 기준을 모두 통과했다.

## Prometheus 직접 조회 결과

결과는 Grafana 패널 값을 사용하지 않고 클러스터 내부 Prometheus HTTP API를 직접
조회했다. Counter는 테스트 직전 11:10:10과 종료 후 11:13:10의 실제 sample 차이,
지속 부하는 11:12:50 시점의 30초 rate로 계산했다.

| 목표 RPS | 실제 RPS | 서버 p50 | 서버 p95 | 서버 p99 |
|---:|---:|---:|---:|---:|
| 200 | 200.00 | 2.12ms | 2.56ms | 3.14ms |

| 항목 | 결과 |
|---|---:|
| click 쓰기 p95 / p99 | 1.30ms / 1.63ms |
| redirect 쓰기 p95 / p99 | 0.99ms / 1.27ms |
| 애플리케이션 CPU 최대, 30초 rate | 286m |
| 애플리케이션 메모리 최대 | 484.19Mi |
| JVM heap 최대 | 150.16Mi |
| GC 평균 pause 최대 | 3.00ms |
| Tomcat busy / current 최대 관측 | 2 / 40 |
| Hikari active / pending 최대 관측 | 1 / 0 |
| Hikari timeout 증가 | 0 |
| Pod restart 증가 | 0 |

CPU throttled period는 전체 1,706개 중 39개로 2.29%였다. 30초 구간 최대 비율은
15.08%였지만 200 RPS 마지막 안정 구간은 0%였다. setup 또는 ramp의 짧은 burst에서
발생한 제한으로 보이며, 지속 처리량이나 지연 악화로 이어지지는 않았다.

## 요청 수와 캐시 검증

- k6 redirect 25,349건과 Prometheus `srrrg.redirect` 증가량 25,349건이 일치했다.
- HTTP route도 302 redirect 25,349건, 링크 생성 20건, 링크 삭제 20건으로 일치했다.
- cache hit는 25,349건이며 `miss_stale`은 0건이었다.
- setup 링크 20개에서만 `miss_absent`와 `fixed_safe safe`가 각각 20건 발생했다.
- HTTP 5xx와 redirect error는 0건이었다.
- teardown에서 테스트 링크 20개를 모두 삭제했다.

## PostgreSQL

| 항목 | 결과 |
|---|---:|
| connection 최대 | 10 |
| 200 RPS 안정 구간 commit/s | 806.75 |
| 전체 commit 증가 | 101,634 |
| rollback / deadlock 증가 | 0 / 0 |
| cache hit ratio | 100% |
| temporary data 증가 | 0 B |
| CPU 최대 | 254m |
| 메모리 최대 | 153.80Mi |
| container read / write 최대 | 0 / 10.69MiB/s |

관측된 lock은 `AccessShareLock` 2개, `RowExclusiveLock` 4개,
`RowShareLock` 2개가 최대였고 exclusive 계열 lock과 deadlock 증가는 없었다.
connection pending, timeout, rollback, temporary data와 cache miss가 없어 현재
200 RPS에서는 DB 또는 HikariCP 병목 징후가 없다. 따라서 상위 SQL 추가 분석은
수행하지 않았다.

## 판정

성공. Warm-cache 정상 상태의 단일 dev Pod는 200 RPS를 2분 동안 목표 처리량
그대로 유지했고, 오류·dropped iteration·restart·connection 대기·DB 오류 없이
처리했다. 서버 p95 2.56ms와 p99 3.14ms로 지연 기준도 충분히 만족했다.

이 결과는 200 RPS의 안정성을 검증하지만 최대 지속 처리량을 의미하지는 않는다.
다음 부하 증가는 한 번에 300 RPS로 점프하지 않고 225 RPS부터 작은 단계로 올려
CPU throttling과 서버 지연의 최초 변곡점을 찾는다.
