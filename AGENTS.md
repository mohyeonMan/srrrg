# srrrg

비회원도 즉시 쓰는 단축 URL 도구이자, 팀이 캠페인 단위로 UTM 링크를 대량 발행하고
채널별 성과를 추적하는 마케팅 링크 플랫폼. 화면과 문구는 한국어가 기본이다.

Java 21 · Spring Boot 4.0.3 · Spring MVC(서블릿) · JPA + PostgreSQL · Redis ·
Thymeleaf SSR + 번들러 없는 바닐라 JS · Lombok · Flyway · Testcontainers

## 명령

~~~bash
./gradlew test          # 전체 테스트. Testcontainers 때문에 Docker 데몬이 떠 있어야 한다
./gradlew bootRun       # 로컬 실행. Postgres·Redis가 먼저 필요하다
docker compose up -d postgres redis
~~~

Windows에서는 ./gradlew 대신 .\gradlew.bat을 사용한다.

## 작업 원칙

- 모든 요청은 조사·계획과 구현을 분리한다. 먼저 읽기 전용으로 충분히 조사하고 구현계획을 제시한다.
- 계획에는 목표, 포함·제외 범위, 변경 예상 파일, 선행 작업, 검증 방법과 위험을 명시한다.
- 사용자가 계획을 명시적으로 승인하기 전에는 파일 수정, 의존성 설치 또는 상태 변경을 시작하지 않는다.
- 승인은 계획에 적힌 작업만 허용한다. 범위를 벗어날 필요가 생기면 즉시 중단하고 다시 승인받는다.
- 분석·설명·검토 요청은 읽기 전용으로 처리한다. 관련 문제를 발견해도 승인 밖이면 관찰 사항으로만 보고한다.
- 작업 시작 전에 git status --short로 기존 변경을 확인하고 사용자 변경을 덮어쓰거나 되돌리지 않는다.
- 사용자가 명시적으로 요청하지 않으면 Git stage, commit, push, 브랜치·태그 생성 또는 이력 변경을 하지 않는다.

상세 작업 절차와 안전 규칙은 docs/development/workflow.md를 따른다.

## 작업별 필수 문서

| 작업 | 먼저 읽을 문서 |
|---|---|
| 코드 작성·수정 | docs/conventions.md, docs/development/workflow.md |
| 로직 작성·수정·주석 정비 | docs/development/commenting.md |
| 테스트·완료 검증 | docs/development/verification.md |
| 구조 설계·리팩터링·감사 | docs/development/architecture-criteria.md |
| 엔드포인트 변경 | 해당 docs/flow/*.md |
| UI 변경 | docs/design-system.md, docs/ia-restructure.md, docs/ui-ux-decisions.md |
| 도메인 변경 | 관련 docs/architecture/*.md |

코드를 변경할 때는 상세한 한국어 주석까지 같은 범위에서 작성·갱신해야 완료된 것으로 본다.

## 구조

~~~text
src/main/java/link/srrrg/     기능별 패키지 (auth · identity · project · campaign · link · statistics · common · domain)
src/main/resources/
  db/migration/               Flyway. 스키마의 유일한 소유자
  templates/                  Thymeleaf. fragments/srrrg-layout.html이 공통 header·footer
  static/css/srrrg.css        @import 목록. 실제 규칙은 static/css/srrrg/*.css
  static/css/srrrg/base.css   디자인 토큰 원본
  static/js/srrrg-*.js        화면별 스크립트. srrrg-common.js가 공통 fetch·401 재시도
docs/flow/                    엔드포인트별 호출 순서 지도
scripts/performance/          k6 시나리오
~~~

## 반드시 지켜야 할 전제

**멀티 파드로 배포된다.**

- 세션은 STATELESS JWT다. 서버 세션·sticky session에 의존하지 않는다.
- 인메모리 상태로 파드 사이를 조율하지 않는다. 레이트리밋은 Redis, 동시성 제어는 DB나 공유 저장소를 사용한다.
- JVM lock은 파드를 가로지르지 못한다. @Scheduled는 모든 파드에서 실행된다는 전제로 중복 실행을 막는다.

**API 표면이 셋이고 계약이 다르다.**

| 표면 | 인증 | 주체 획득 | 오류 형식 | CSRF |
|---|---|---|---|---|
| /api/web/** | 쿠키 JWT | @AuthenticationPrincipal SrrrgPrincipal | ApiErrorResponse | 적용 |
| /api/v1/** | Bearer srrrg_pk_... | request 속성 srrrg.apiKeyPrincipal | RFC 7807 ProblemDetail | 면제 |
| /api/links/**, /{code} | 없음 (+ X-Srrrg-Secret-Key) | 없음 | ApiErrorResponse | 면제 |

- 같은 기능의 웹·API 키 경로가 함께 존재한다. 한쪽만 고쳐 정책이 어긋나지 않게 관련 경로를 모두 확인한다.
- 스키마는 Flyway가 소유한다. 이미 적용된 migration은 수정하지 않고 새 버전을 추가한다.
- ddl-auto는 validate, open-in-view는 false를 유지한다.
- secret key, API key, refresh token, 초대 토큰, OAuth 연결 토큰은 원문을 저장하지 않는다.
- users.email에는 OAuth 공급자가 검증한 주소만 저장한다.

## 안전과 호환성

- 루트 .env와 인증서·키·토큰 원문은 명시적 요청 없이 읽거나 출력하지 않는다. 설정은 변수명과 존재 여부만 확인한다.
- API 경로, 상태 코드, 요청·응답 필드, 오류 코드, 인증·CSRF·멱등성 계약을 별도 승인 없이 변경하지 않는다.
- 요청과 무관한 대량 포맷, 일괄 치환, 생성 파일 변경을 하지 않는다.
- 예외를 삼키거나 보안 실패 정책을 임의로 fail-open으로 바꾸지 않는다.
- 커밋 메시지에는 실제 변경 내용만 쓰며 AI 제품·모델·도구, 생성 표기, 공동 작성자, 서명 또는 관련 링크를 넣지 않는다.

## 완료 조건

- 승인된 범위만 변경했다.
- 관련 주석과 권위 있는 문서를 갱신했다.
- 관련 테스트를 실행하고 실제 결과를 확인했다.
- diff에서 범위 밖 변경과 사용자 변경 훼손이 없는지 확인했다.
- 실행하지 못한 검증, 실패와 남은 위험을 숨기지 않고 보고했다.

## 문서 권위

| 주제 | 현재 기준 |
|---|---|
| 작업 절차 | docs/development/workflow.md |
| 코드·커밋·API·DB 컨벤션 | docs/conventions.md |
| 주석 | docs/development/commenting.md |
| 검증 | docs/development/verification.md |
| 구조 평가 | docs/development/architecture-criteria.md |
| 엔드포인트 호출 흐름 | docs/flow/*.md |
| 시각 토큰·컴포넌트 | docs/design-system.md |
| 화면 정보구조·탭 | docs/ia-restructure.md |
| UI 결정 근거 | docs/ui-ux-decisions.md |
| 도메인 구조 | docs/architecture/campaign_utm_templates.md, docs/architecture/project_campaign_personalized_links.md |

UI를 바꿨으면 docs/ui-ux-decisions.md에 결정 근거를 덧붙인다.
엔드포인트 흐름을 바꿨으면 해당 docs/flow/*.md를 같이 고친다.

## 제외할 낡은 자료와 함정

- docs/design/design-conventions.md와 docs/design/v2, docs/design/v3는 현재 기준으로 사용하지 않는다.
- docs/srrrg_v1_*.md, docs/implement/*, docs/*-audit.md는 당시 기록이며 히스토리 확인에만 사용한다.
- docs/performance/results/**는 원시 결과이므로 전체 검색에서 제외한다.
- src/main/resources/templates_old/는 아무 데서도 참조하지 않는 잔재다.
