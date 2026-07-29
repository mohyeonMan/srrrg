# SHA-256 Mixed workload VU 여유 재시험 분석

## 판정

성공. redirect 500 RPS, cache-hit 링크 생성 4 RPS, cache-miss 링크 생성
1 RPS를 2분간 함께 실행해 모든 threshold를 통과했다. HTTP 실패와 dropped
iteration은 0이었다.

k6의 p95/p99는 redirect 25.68/102.58ms, hit 생성 22.73/81.26ms,
miss 생성 135.27/199.43ms였다. redirect VU는 사전 500개, 최대 1,000개로
열어뒀지만 실제 최대 사용량은 93개였다.

## 서버 상태

Prometheus 서버 redirect p95/p99는 4.28/21.53ms, 링크 생성 p95/p99는
109.26/111.67ms였다. 애플리케이션 CPU는 평균 0.375코어, 최대 0.675코어였고
CPU throttled period는 전체 3.09%, 30초 최대 13.73%였다. Tomcat busy 최대
5, Hikari active/pending 최대 3/0, Hikari timeout과 Pod restart는 0이었다.

직전 실행의 dropped iteration 1,083건은 지속적인 서버 포화가 아니라 당시의
일시적인 client/network tail이 200 VU를 소진해 발생한 것으로 판정한다. 이번
실행에서는 redirect waiting p95가 25.48ms였고 모든 구간이 정상적으로 끝났다.
