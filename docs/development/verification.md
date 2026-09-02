# 변경 검증 기준

검증은 변경이 의도한 결과를 만들었는지와 승인 범위 밖 동작을 바꾸지 않았는지를 확인하는 과정이다.

## 1. 공통 원칙

- 변경한 로직과 가장 가까운 검증부터 실행한 뒤 필요에 따라 범위를 넓힌다.
- 실제로 실행하지 않은 테스트나 검사를 통과했다고 보고하지 않는다.
- 테스트를 통과시키기 위해 assertion을 약화하거나 테스트를 삭제하지 않는다.
- 실패를 기존 문제라고 단정하려면 변경 전에도 같은 조건에서 재현되는지 확인한다.
- 명령이 실행되지 않거나 환경이 부족하면 원인과 미검증 범위를 그대로 보고한다.

## 2. 변경 유형별 검증

| 변경 | 최소 검증 |
|---|---|
| Java 업무 로직 | 대상 단위 테스트 또는 통합 테스트, 컴파일 |
| Controller·보안 설정 | 관련 HTTP·보안 테스트 |
| Repository·JPA·PostgreSQL | 관련 Testcontainers 통합 테스트 |
| JavaScript·template | 관련 asset 테스트와 영향 화면의 DOM·접근성 흐름 확인 |
| Flyway migration | 새 migration만 추가했는지 확인, PostgreSQL 통합 검증 |
| 설정 | 프로파일별 바인딩과 시작 가능 여부 확인 |
| 문서만 변경 | 링크·경로·명령의 유효성, diff와 whitespace 확인 |

Windows에서는 다음 형식을 사용한다.

~~~powershell
.\gradlew.bat test --tests fully.qualified.TestClass
.\gradlew.bat test
~~~

Unix 계열에서는 Gradle wrapper를 사용한다.

~~~bash
./gradlew test --tests fully.qualified.TestClass
./gradlew test
~~~

전체 테스트는 Docker가 필요한 Testcontainers 테스트를 포함한다. Docker를 사용할 수 없으면 통과했다고 간주하지 않는다.

## 3. 버그와 회귀

- 버그 수정에는 수정 전 실패하고 수정 후 통과하는 재현 검증을 포함한다.
- private 메서드 구현보다 외부에서 관찰 가능한 정책과 결과를 검증한다.
- 시간과 동시성 테스트는 임의 sleep에 의존하지 않고 제어 가능한 시계나 동기화 지점을 우선한다.
- 예외 종류뿐 아니라 상태 변경, 저장 여부와 이벤트 기록도 필요한 범위에서 검증한다.

## 4. 완료 전 점검

- git diff --check를 실행한다.
- git diff와 git status --short로 범위 밖 변경과 사용자 변경 훼손을 확인한다.
- 주석과 권위 있는 문서가 실제 구현과 일치하는지 확인한다.
- API, DB, 보안과 운영 계약에 의도하지 않은 변화가 없는지 확인한다.

## 5. 결과 보고

다음 형식으로 사실만 보고한다.

- 실행: 실제 실행한 명령
- 성공: 통과한 테스트와 검사
- 실패: 실패한 검사와 핵심 원인
- 미실행: 실행하지 못한 검증과 이유
- 잔여 위험: 아직 확인되지 않은 영향
