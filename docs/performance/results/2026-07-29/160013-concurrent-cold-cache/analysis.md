# Concurrent cold-cache 50 VU 분석

## 결과

동시 redirect 50건이 모두 성공했다. 이 중 47건은 `miss_stale`과 fixed-safe 검사를
수행했고 3건은 앞선 검사 결과를 cache hit로 재사용했다. 고유 대상 URL이 1개이므로
중복 검사 배수는 47배다.

| 동시 VU | fixed-safe 검사 | cache hit | 중복 검사 배수 |
|---:|---:|---:|---:|
| 10 | 10 | 0 | 10배 |
| 25 | 25 | 0 | 25배 |
| 50 | 47 | 3 | 47배 |

k6 p95/p99는 240.90ms/325.20ms, 서버 p95/p99는 222.84ms/259.22ms였다.
fixed-safe p95는 151.55ms, redirect write p95는 92.48ms였다. HTTP 실패,
Hikari timeout과 PostgreSQL deadlock은 0이었다.

## 판정

현재 구현에는 같은 URL에 대한 in-flight 검사 공유가 없다. 100ms 외부 검사 중 겹친
요청은 대부분 독립적으로 fixed-safe 검사를 수행하며, 동시 요청 수에 거의 비례해
외부 호출과 캐시 저장이 증가한다.

이번 테스트 목적은 현재 중복 수준 측정이며 즉시 개선을 전제하지 않는다. 운영에서
동일 URL의 동시 miss가 자주 발생하거나 외부 API quota·비용 문제가 나타날 때
single-flight 또는 짧은 분산 lock을 별도 검토한다.
