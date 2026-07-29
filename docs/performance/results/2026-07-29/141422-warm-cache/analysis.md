# Warm-cache 850 RPS 재시험 분석

## 판정

실패. 최초 실행의 k6 VU 200 제한 영향을 제거하기 위해 최대 VU를 300으로 올려
재시험했지만 p95/p99 239.97ms/394.12ms, dropped iteration 63건으로 다시
자동 중단됐다. 워밍업은 기존 10 RPS 30초를 그대로 사용했다.

## Prometheus 병목 확인

| 항목 | 결과 |
|---|---:|
| 최대 서버 처리량 | 572.30 RPS |
| 서버 p95 / p99 최대 | 298.82ms / 446.59ms |
| redirect DB 쓰기 p95 최대 | 98.23ms |
| 앱 CPU / throttling 최대 | 112m / 0% |
| Hikari active / pending 최대 | 10 / 189 |
| PostgreSQL connection 최대 | 10 |
| PostgreSQL CPU 최대 | 341m |
| restart / Hikari timeout / rollback / deadlock | 모두 0 |

850 RPS로 올라가는 과정에서 Hikari connection 10개가 모두 사용됐고 pending이
189까지 증가했다. 앱과 PostgreSQL CPU는 낮고 CPU throttling도 없어 CPU 부족은
아니다. 일반적인 AccessShare/RowExclusive/RowShare lock만 관측됐고 exclusive lock,
rollback과 deadlock은 없으므로 lock 충돌도 주원인이 아니다.

현재 한계는 Hikari pool 10과 redirect 쓰기 트랜잭션 동시성에서 발생한 connection
대기다. timeout이 0인 것은 threshold가 먼저 테스트를 중단했기 때문이며, pending
189 자체로 이미 포화가 확인된다.

## 결론

현재 설정에서 단일 Pod의 검증된 최대 지속 처리량은 800 RPS이며 850 RPS는
재시험에서도 실패했다. 운영 목표 500 TPS는 검증 상한의 62.5%다. Hikari pool을
늘려 850 RPS 이상을 재시험하는 것은 운영 목표 달성에 필요하지 않으며, PostgreSQL
connection 예산과 replica 2·3 구성을 함께 결정할 때 별도 검토한다.
