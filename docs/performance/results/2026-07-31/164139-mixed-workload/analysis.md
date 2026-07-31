# Mixed workload 30분 Soak 분석

## 판정

실패. 고부하 혼합 Soak에서 HTTP 502 두 건과 dropped iteration 320건이 발생했고, 애플리케이션과 PostgreSQL 포화 지표가 동반됐다.

## 실행 조건

| 항목 | 값 |
|---|---:|
| 실행 시각 | 2026-07-31T16:41:39+09:00 ~ 2026-07-31T17:12:24+09:00 |
| 애플리케이션 SHA | bfd78f2 |
| 인프라 SHA | 99f2520 |
| replica | 2 |
| 부하 | redirect 1,200 RPS, hit 400 RPS, miss 150 RPS |
| 실행 시간 | 30분 (+ warmup/ramp) |
| fixed-safe 설정 | 100ms / 15m |
| Hikari pool | 10 |

## k6 결과

| 항목 | 결과 |
|---|---:|
| HTTP 요청 | 3,156,055 |
| HTTP 실패 | 2건 (502) |
| dropped iteration | 320건 |
| client p50 | redirect 10.19ms; hit 10.05ms; miss 111.01ms |
| client p95 | redirect 27.18ms; hit 26.10ms; miss 129.07ms |
| client p99 | redirect 234.42ms; hit 196.77ms; miss 360.96ms |

## Prometheus 결과

| 항목 | 결과 |
|---|---:|
| 서버 요청 | 3,152,946 (counter extrapolation) |
| server p50 | redirect 1.67ms; link 1.37ms |
| server p95 | redirect 4.02ms; link 110.30ms |
| server p99 | redirect 107.86ms; link 153.70ms |
| Pod별 처리량 | 1,576,472 / 1,576,474 |
| CPU 평균/최대 | 0.633/0.881, 0.633/0.905 core |
| memory 최대 | 502,677,504 / 512,966,656 bytes |
| Tomcat busy 최대 | 200 / 125 |
| Hikari active/pending | 10/180, 10/106 |
| Hikari timeout | 0 |
| PostgreSQL CPU | 0.840 / 1.153 core |
| PostgreSQL connection | 20 |
| rollback/deadlock | 약 9 / 0 |

## 정합성

- k6 3,156,055건과 Prometheus 서버 요청 3,152,946건의 차이는 setup/teardown 및 scrape 경계와 counter extrapolation에 따른 것이다.
- Pod별 서버 요청 합계는 전체 Prometheus 요청과 일치한다.
- cache hit/miss와 fixed-safe 검사 증가량은 `prometheus-result.json`에 기록했다.

## 병목 또는 회복

지속적인 포화 징후가 있었다. Pod CPU throttling이 최대 32.78%/31.18%였고, Hikari pending은 180/106까지 증가했으며 한 Pod의 Tomcat busy가 200에 도달했다. PostgreSQL CPU도 최대 1.153 core에 도달했고 502와 rollback 증가가 함께 관측됐다. 따라서 dropped iteration 320건은 클라이언트 환경만의 문제로 판단할 수 없다.

## 결론

- 검증된 범위: replica 2에서 redirect 1,200 RPS, hit 400 RPS, miss 150 RPS를 30분 실행.
- 실패 경계: 해당 혼합 부하는 현재 리소스에서 지속 가능한 부하를 초과했다.
- 현재 운영 의미: 이 혼합 비율의 장기 운영 부하는 1,750 RPS 합계보다 낮아야 한다.
- 다음 테스트가 필요한지: 서버 포화와 장기 안정성의 경계를 찾으려면 이 결과보다 낮은 혼합 부하를 별도 실행해야 한다.
- 결과의 한계: k6 호스트 CPU/메모리는 수집하지 않아 dropped의 기여도를 정량 분리하지 못했다.
