# Mixed workload 30분 Soak 분석

## 판정

실패. redirect 500 RPS, cache-hit 링크 생성 4 RPS, cache-miss 링크 생성
1 RPS를 30분간 실행했으나 redirect p95/p99는 455.27ms/2.42초,
dropped iteration은 37,125건, HTTP 실패는 469건이었다.

실패는 시간이 지날수록 계속 느려진 형태가 아니라 실행 약 4분 뒤 발생한 포화
구간이 전체 결과를 악화시킨 형태다. Prometheus 30초 간격 61개 표본 중 서버
redirect p95가 250ms를 넘은 표본은 6개였고, 최대 p95/p99는
2.11/2.80초였다.

## 병목과 회복

포화 시 애플리케이션 CPU throttled period 100%, Tomcat busy 200,
Hikari active/pending 10/189가 함께 관측됐다. Hikari timeout은 실행 구간에서
468건 증가했고 PostgreSQL CPU 최대값은 0.45코어, deadlock은 0이었다.
단일 Pod의 500 RPS 혼합 부하는 평상시 처리되지만 순간적인 tail이 발생하면
CPU·thread·connection 대기가 연쇄 포화될 여유가 부족하다.

5초 단위로 최초 포화 구간을 다시 확인한 결과, 22:14:35에 Tomcat busy가
2에서 37로, Hikari active/pending이 1/0에서 8/28로 먼저 증가했다.
22:14:40에는 PostgreSQL `transactionid` lock wait와 idle-in-transaction
연결 6개가 관측됐고, 22:14:50에는 `WALSync`와 `WALWrite` wait가 이어졌다.
완료된 요청에 기록되는 redirect 지연은 이보다 늦게 상승했다.

click 쓰기와 redirect 쓰기를 분리하면 정상 평균은 각각 0.92/0.84ms였지만
22:14:45에는 4.06/2.18ms로 click 쓰기가 먼저 느려졌다. 현재 redirect 한 건은
click·redirect 이벤트를 각각 insert하고 같은 link row의 두 counter를 각각
update한다. 30분 동안 insert가 1,734,194건 증가한 것도 요청당 두 이벤트 쓰기와
일치한다. 따라서 최초 trigger를 단일 요청까지 특정할 수는 없지만, 동기식 쓰기의
row lock 및 WAL 지연이 connection을 오래 점유하고 CPU·Tomcat 포화로 증폭된
것이 이번 tail의 직접적인 병목이다.

실행 종료 시에는 redirect 500 RPS, 서버 p95/p99 4.41/60.40ms,
CPU throttling 1.75%, Tomcat busy 3, Hikari pending 0으로 회복했다.
Pod restart는 없었다.

## 장기 추세

컨테이너 working set은 460.8MB에서 528.1MB로 증가했다. JVM heap used는
124.2MB에서 165.6MB로 증가했지만 GC 후 live data는 59.3MB에서 57.9MB로
감소했으므로 이번 30분 실행에서 retained heap 누수 징후는 없다. GC pause
누계는 3.521초였다.

`srrrg-dev` DB 크기는 544,390,167바이트에서 872,381,463바이트로
327,991,296바이트(약 312.8MiB) 증가했다. 이 부하를 장시간 유지할 때는
메모리보다 redirect 쓰기 데이터의 저장 공간 증가가 먼저 관리 대상이다.
