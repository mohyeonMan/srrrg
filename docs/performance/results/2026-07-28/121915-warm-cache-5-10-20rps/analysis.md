# Warm-cache calibration 중단 분석

## 결과

부하 구간 시작 전 setup 첫 링크 생성 직후 Windows PowerShell 래퍼가 중단됐다.
k6 `console.log`가 stderr로 출력됐고 Windows PowerShell 5가 이를
`NativeCommandError`로 처리한 것이 원인이다. 애플리케이션 오류가 아니다.

- 생성된 테스트 링크: 1개
- redirect 부하 요청: 0개
- 생성된 링크 정리: 완료
- 후속 조치: PowerShell 래퍼가 정상 stderr 로그를 중단 조건으로 취급하지 않도록 수정

이 실행은 성능 결과로 사용하지 않는다.
