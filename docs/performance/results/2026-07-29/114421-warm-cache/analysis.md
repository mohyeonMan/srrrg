# Warm-cache 300 RPS 보정 재검증 분석

## 실행 정보

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-07-29 11:44:21 ~ 11:47:08 KST |
| 대상 | `https://jhhomehub.gonetis.com/srrrg-dev` |
| 워밍업 | 10 RPS, 30초, 300 redirect |
| 본 측정 | 10초 ramp 후 300 RPS, 2분 유지 |
| 테스트 링크 | 20개 |
| pre-allocated / max VU | 60 / 200 |
| 애플리케이션 commit | `e7484af47a90658417d8c35b40f0a4ec28175158` |
| 인프라 commit | `40757b82baa97a336e897c12833c20697130a50b` |
| Pod | dev 1 replica, CPU 500m / memory 1Gi limit |
| URL 위험 검사 | `fixed-safe`, delay `100ms`, cache duration `15m` |
| k6 종료 코드 | 0 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| 워밍업 redirect | 300 |
| 본 측정 redirect | 37,550 |
| 전체 redirect | 37,850 |
| 전체 HTTP 요청 | 37,890 |
| check | 37,950 / 37,950 성공 |
| HTTP 실패 | 0 |
| dropped iteration | 0 |
| 본 측정 p95 | 12.60ms |
| 본 측정 p99 | 19.24ms |
| 본 측정 최대 | 56.27ms |
| 최대 활성 VU | 8 |

## Prometheus 직접 조회 결과

Counter는 테스트 직전 11:44:15와 종료 후 11:47:15 sample의 차이로 계산했고,
300 RPS 수치는 11:46:55 시점의 30초 rate를 사용했다.

| 목표 RPS | 실제 RPS | 서버 p50 | 서버 p95 | 서버 p99 |
|---:|---:|---:|---:|---:|
| 300 | 300.00 | 1.97ms | 2.56ms | 3.10ms |

| 항목 | 결과 |
|---|---:|
| click / redirect 쓰기 p95 | 1.26ms / 1.01ms |
| 애플리케이션 CPU 최대 | 351m |
| 애플리케이션 메모리 최대 | 485.53Mi |
| JVM heap 최대 | 150.47Mi |
| GC 평균 pause 최대 | 1.50ms |
| Tomcat busy / current 최대 관측 | 3 / 15 |
| Hikari active / pending 최대 관측 | 2 / 0 |
| Hikari timeout / Pod restart 증가 | 0 / 0 |

CPU throttled period는 전체 1,654개 중 11개로 0.67%였다. 30초 최대 비율은
13.51%였지만 300 RPS 안정 시점은 0%였고 처리량과 지연 악화는 없었다.

## 요청 수와 캐시 검증

- k6 redirect 37,850건과 Prometheus redirect 및 cache hit 증가량이 일치했다.
- HTTP route도 302 redirect 37,850건, 링크 생성·삭제 각 20건으로 일치했다.
- `miss_stale`은 0건이고 setup에서만 `miss_absent`와 risk check가 각 20건 발생했다.
- HTTP 5xx와 redirect error는 0건이었다.

## PostgreSQL

| 항목 | 결과 |
|---|---:|
| 300 RPS commit/s | 1,206.30 |
| connection 최대 | 10 |
| 전체 commit 증가 | 148,906 |
| rollback / deadlock 증가 | 0 / 0 |
| cache hit ratio | 100% |
| temporary data 증가 | 0 B |
| CPU 최대 | 355m |
| 메모리 최대 | 199.81Mi |
| container write 최대 | 16.54MiB/s |

일반적인 access/row lock만 관측됐고 exclusive 계열 lock, deadlock, rollback,
Hikari pending과 timeout이 없어 DB 병목 징후는 없다. 상위 SQL 추가 분석은
수행하지 않았다.

## 이전 300 RPS 실패와 비교

이전 실행은 워밍업 직후 300 RPS로 즉시 점프했고 최대 VU가 1,200까지 증가하면서
요청이 수 초씩 누적됐다. Tomcat thread 200 포화, Hikari timeout과 Pod restart가
뒤따랐다.

이번 실행은 10초 ramp, pre-allocated VU 60, 최대 VU 200을 사용했다. 실제 활성
VU는 최대 8이었고 Tomcat busy 최대 3, Hikari pending·timeout과 restart는 모두
0이었다. 따라서 이전 실패는 300 RPS의 지속 처리 한계가 아니라 잘못된 부하 상승과
무제한에 가까운 동시 요청 누적이 만든 테스트 인공 장애로 판정한다.

## 판정

성공. 단일 dev Pod는 Warm-cache redirect 300 RPS를 2분 동안 목표 처리량 그대로
유지했다. 서버 지연, CPU, thread, connection과 PostgreSQL 모두 지속 포화 징후가
없었다.

최대 지속 처리량은 아직 확인되지 않았다. 다음 탐색은 350 RPS부터 50 RPS 단위로
진행하며 동일한 ramp, VU 상한과 자동 중단 기준을 유지한다.
