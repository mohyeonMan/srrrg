# Warm-cache 850 RPS 최초 기준 초과 분석

## 판정

실패. 850 RPS 구간에서 k6가 VU 200 제한에 도달했고 dropped iteration 156건이
발생했다. 클라이언트 p99도 281.5ms로 250ms 기준을 초과해 테스트가 자동 중단됐다.
상위 부하는 진행하지 않았다.

## 최초 실행에서 확인한 내용

| 항목 | 결과 |
|---|---:|
| k6 p95 / p99 | 39.30ms / 281.50ms |
| HTTP 실패 | 0 |
| 최대 활성 VU | 191, 초기화 한도 200 도달 |
| Prometheus 최대 실제 RPS | 850.60 |
| 실패 직전 서버 p95 / p99 | 2.94ms / 3.65ms |
| redirect DB 쓰기 p95 | 1.37ms |
| 앱 CPU 최대 | 735m |
| CPU throttling 최대 | 3.31% |
| Hikari active / pending 최대 | 3 / 0 |
| PostgreSQL CPU 최대 | 691m |
| restart / timeout / deadlock | 모두 0 |

서버는 짧게 850 RPS까지 처리했지만 최초 Prometheus 조회 시간창이 실패 peak를
정확히 포함하지 못했다. 따라서 이 실행만으로 k6 VU 제한과 서버 병목을 구분하지
않는다.

`WARM_MAX_VUS=300`으로 재시험한 `141422-warm-cache`에서 서버 지연과 Hikari
pending을 포함해 다시 판정했다.
