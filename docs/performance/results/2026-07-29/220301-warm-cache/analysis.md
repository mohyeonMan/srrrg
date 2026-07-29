# Warm-cache 500→1,000→500 RPS Spike 분석

## 판정

Spike 구간은 포화됐지만 이후 500 RPS로 정상 회복했다. 목표 1,000 RPS에서
Prometheus 관측 처리량은 최대 734 RPS였고 k6는 최대 VU 500개를 소진해
dropped iteration 10,186건을 기록했다. HTTP 오류는 0이었다.

Spike 구간의 서버 redirect p95/p99는 625.80/881.41ms, CPU throttled period는
91.41%, Tomcat busy는 200, Hikari active/pending은 10/189까지 상승했다. 현재
단일 Pod가 1,000 RPS Spike를 흡수하지 못하는 원인은 CPU throttling 이후
Tomcat과 DB connection 대기가 함께 포화되는 것이다.

## 회복

부하를 다시 500 RPS로 낮춘 마지막 구간 종료 시 처리량은 500 RPS,
서버 redirect p95/p99는 2.98/4.46ms, Tomcat busy는 3, Hikari pending은
0이었다. 애플리케이션 CPU도 종료 직전 0.383코어로 내려왔다. Hikari timeout과
Pod restart는 없었다.

따라서 1,000 RPS 자체는 실패했지만 Spike 종료 후 지연, thread와 connection은
30초 안에 정상 상태로 회복했다.
