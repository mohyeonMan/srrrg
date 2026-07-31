# Baseline 성능 테스트

## 목적

최신 빌드의 낮은 부하 기준값을 남긴다. VU 1이 링크 생성, 관리 조회, 캐시된
redirect와 테스트 링크 삭제를 1분간 순서대로 반복한다. 이 결과는 최대 처리량이나
SLO가 아니라 이후 변경 전후 비교 기준이다.

## 실행

```powershell
.\scripts\performance\run.ps1 -Scenario baseline
```

실행 시간은 `BASELINE_DURATION`으로 변경한다.

```powershell
$env:BASELINE_DURATION = '2m'
.\scripts\performance\run.ps1 -Scenario baseline
```

## 기록

- `baseline_link_create`, `baseline_management_get`, `baseline_redirect`의 p50·p95·p99
- HTTP 실패와 check 결과
- 애플리케이션 CPU·메모리, Hikari active·pending
- PostgreSQL CPU·connection
- 전체 요청 수와 Pod별 요청 수 합계

실행 후 Prometheus 결과와 판정을 결과 디렉터리에 기록한다.
