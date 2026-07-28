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
| `metadata.json` | 공용 실행기 | 실행 시각, 시나리오, commit SHA, 종료 코드와 분석 상태 |
| `k6-output.log` | 공용 실행기 | k6 설정, check, threshold와 실행 요약 원본 |
| `prometheus-result.json` | 실행 후 분석 | 평가 시각, PromQL 결과와 단계별 서버 지표 |
| `analysis.md` | 실행 후 분석 | 결과 요약, 병목 해설, 한계, 판정과 다음 테스트 |

공용 실행 직후 `metadata.json`의 `analysisStatus`는 `pending`이다. Prometheus 조회와
해설 기록이 끝나면 `complete`로 변경한다.

## 분석 절차

1. k6 종료 코드, 실패율, check, threshold와 dropped iteration을 확인한다.
2. SSH로 클러스터의 Prometheus API를 조회한다.
3. 테스트 실행 전후 Counter와 단계별 RPS가 k6 요청 수와 일치하는지 비교한다.
4. HTTP 및 커스텀 Timer의 p50, p95, p99를 확인한다.
5. HikariCP, Tomcat, Pod CPU·메모리와 PostgreSQL 지표를 함께 확인한다.
6. 조회값을 `prometheus-result.json`에 저장하고 해설과 판정을 `analysis.md`에 작성한다.

## 기록 원칙

- 평균 하나로 합치지 않고 시나리오와 부하 단계별로 기록한다.
- Prometheus histogram 값은 평가 시각과 rate window를 함께 남긴다.
- 짧은 실행으로 scrape 사이의 gauge 최고치를 놓쳤다면 분석 한계에 명시한다.
- 실행 중 배포, Pod restart 또는 설정 변경이 있었다면 결과 비교에서 제외하거나 표시한다.
- 다음 실행은 직전 결과에서 확인된 병목과 판정 기준을 근거로 정한다.
