# Warm-cache 350~500 RPS 실행 중 배포 중첩 분석

## 판정

성능 판정 제외. 350 RPS 첫 단계가 완료되기 전에 dev Deployment rollout이 겹쳤다.
이 실행으로 변경 리소스의 350 RPS 처리 능력이나 한계를 판단하지 않는다.

## 실행 및 중단

| 항목 | 결과 |
|---|---:|
| 설정 | 350 → 400 → 450 → 500 RPS |
| 완료한 본 측정 단계 | 없음 |
| k6 완료 / 중단 iteration | 8,397 / 59 |
| HTTP 실패 | 0 |
| dropped iteration | 7 |
| k6 p95 / p99 | 192.79ms / 288.05ms |
| 최대 활성 VU | 85 |
| 종료 코드 | 99 |

## 배포 중첩

테스트는 12:49:50에 시작했다. 새 이미지
`ghcr.io/mohyeonman/srrrg:sha-b41e3a6`의 Pod가 12:50:43에 생성돼 첫 350 RPS
구간과 겹쳤다.

12:50:35에는 기존 Pod가 158.4 RPS를 처리하면서 서버 p95 27.82ms, Tomcat busy 2,
Hikari pending 0이었다. 12:50:50에는 기존 Pod가 349.9 RPS를 처리하고 있었지만 새
Pod는 아직 Ready가 아니었고 서버 p95가 115.66ms, Tomcat busy가 14로 증가했다.

Prometheus redirect 증가량은 8,456건이다. k6 중단 시 진행 중이던 59개 요청이
서버에서 완료돼 k6 완료 iteration 8,397건보다 59건 많다. 5xx와 Hikari pending은
관측되지 않았다.

## 다음 실행

새 Deployment가 안정된 뒤 같은 설정과 데이터로 350 → 400 → 450 → 500 RPS를
다시 실행한다. 이번 실행은 변경 전후 리소스 비교나 최대 지속 처리량 계산에 포함하지
않는다.
