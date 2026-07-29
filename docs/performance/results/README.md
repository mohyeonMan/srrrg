# 성능 테스트 결과 저장 규칙

## 디렉터리

성능 테스트 실행 하나를 다음 디렉터리 하나로 관리한다.

```text
docs/performance/results/YYYY-MM-DD/HHmmss-<scenario>/
```

재현과 비교가 가능하도록 성공한 실행뿐 아니라 setup, 실행기 또는 threshold 문제로
중단된 실행도 삭제하지 않고 원인과 정리 여부를 기록한다.

## 파일

| 파일 | 생성 주체 | 내용 |
|---|---|---|
| `metadata.json` | 공용 실행기 | 실행 시각, 애플리케이션·인프라 commit SHA, replica와 resource limit, HikariCP pool size, 시나리오 설정, 종료 코드와 분석 상태 |
| `k6-output.log` | 공용 실행기 | k6 설정, check, threshold와 실행 요약 원본 |
| `prometheus-result.json` | 실행 후 분석 | 평가 시각, PromQL 결과와 단계별 서버 지표 |
| `analysis.md` | 실행 후 분석 | 결과 요약, 병목 해설, 한계, 판정과 다음 테스트 |

공용 실행 직후 `metadata.json`의 `analysisStatus`는 `pending`이다. Prometheus 조회와
해설 기록이 끝나면 `complete`로 변경한다.

## 분석 절차

1. k6 종료 코드, 실패율, check, threshold와 dropped iteration을 확인한다.
2. 실행 시작·종료 시각과 각 부하 단계의 절대 시간 구간을 확정한다.
3. 해당 구간을 대상으로 클러스터의 Prometheus HTTP API를 직접 조회한다.
4. 테스트 실행 전후 Counter와 단계별 RPS가 k6 요청 수와 일치하는지 비교한다.
5. HTTP 및 커스텀 Timer의 p50, p95, p99를 확인한다.
6. HikariCP, Tomcat, Pod CPU·메모리와 PostgreSQL 지표를 함께 확인한다.
7. 사용한 PromQL, 조회 구간, step과 반환값을 `prometheus-result.json`에 저장한다.
8. 저장된 Prometheus 결과를 근거로 해설과 판정을 `analysis.md`에 작성한다.

Prometheus API 조회는 모든 성공·실패 테스트에 적용한다. 조회가 완료되지 않은 실행은
`analysisStatus: pending`으로 유지한다. Grafana는 전체 흐름이나 이상 시점을 탐색하는
보조 수단이며, 패널을 육안으로 읽은 값만으로 결과를 확정하지 않는다.

## 기록 원칙

- 평균 하나로 합치지 않고 시나리오와 부하 단계별로 기록한다.
- Prometheus histogram 값은 평가 시각과 rate window를 함께 남긴다.
- CPU와 gauge는 `최대값`, `구간 평균`, `평가 시점의 rate` 등 실제 수집 방식을 이름에 명시한다.
- secret key, 인증 header와 그 밖의 인증정보는 로그나 결과 파일에 저장하지 않는다.
- 짧은 실행으로 scrape 사이의 gauge 최고치를 놓쳤다면 분석 한계에 명시한다.
- 실행 중 배포, Pod restart 또는 설정 변경이 있었다면 결과 비교에서 제외하거나 표시한다.
- 다음 실행은 직전 결과에서 확인된 병목과 판정 기준을 근거로 정한다.
- 지연 변곡점이나 DB 병목이 관측된 경우에만 상위 SQL과 PostgreSQL I/O를 추가 분석한다.
