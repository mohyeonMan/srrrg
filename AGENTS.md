# srrrg

비회원도 즉시 쓰는 단축 URL 도구이자, 팀이 캠페인 단위로 UTM 링크를 대량 발행하고
채널별 성과를 추적하는 마케팅 링크 플랫폼. 화면과 문구는 한국어가 기본이다.

Java 21 · Spring Boot 4.0.3 · Spring MVC(서블릿) · JPA + PostgreSQL · Redis ·
Thymeleaf SSR + 번들러 없는 바닐라 JS · Lombok · Flyway · Testcontainers

## 명령

```bash
./gradlew test          # 전체 테스트. Testcontainers 때문에 Docker 데몬이 떠 있어야 한다
./gradlew bootRun       # 로컬 실행. Postgres·Redis가 먼저 필요하다
docker compose up -d postgres redis
```

- 비밀값은 루트 `.env`에 두고 커밋하지 않는다(gitignore 처리됨).
- 커밋 전에 `./gradlew test`가 통과하는지 확인한다.

## 컨벤션

코드·커밋·DB·테스트 컨벤션은 `docs/conventions.md`에 있다. 코드를 쓰거나 커밋하기 전에 읽는다.

## 구조

```text
src/main/java/link/srrrg/     기능별 패키지 (auth · identity · project · campaign · link · statistics · common · domain)
src/main/resources/
  db/migration/               Flyway. 스키마의 유일한 소유자
  templates/                  Thymeleaf. fragments/srrrg-layout.html 이 공통 header·footer
  static/css/srrrg.css        @import 목록. 실제 규칙은 static/css/srrrg/*.css
  static/css/srrrg/base.css   디자인 토큰 원본
  static/js/srrrg-*.js        화면별 스크립트. srrrg-common.js 가 공통 fetch·401 재시도
docs/flow/                    엔드포인트별 호출 순서 지도. 코드 읽기 전에 여기부터
scripts/performance/          k6 시나리오
```

## 반드시 지켜야 할 전제

**멀티 파드로 배포된다.** k3s Deployment이고 파드가 여럿이다. 이 사실이 코드 곳곳을 결정한다.

- 세션은 STATELESS JWT다. 서버 세션·sticky session에 의존하는 코드를 쓰지 않는다.
- 인메모리 상태로 조율하지 않는다. 레이트리밋 카운터는 Redis에, 동시성 제어는 DB에 둔다
  (`FOR UPDATE`, `SKIP LOCKED`, lease). JVM 락은 파드를 가로지르지 못한다.
- `@Scheduled`는 모든 파드에서 돈다. 중복 실행을 막는 장치를 함께 넣는다.

**API 표면이 셋이고 규칙이 다르다.**

| 표면 | 인증 | 주체 획득 | 오류 형식 | CSRF |
|---|---|---|---|---|
| `/api/web/**` | 쿠키 JWT | `@AuthenticationPrincipal SrrrgPrincipal` | `ApiErrorResponse` | 적용 |
| `/api/v1/**` | `Bearer srrrg_pk_...` | `request.getAttribute("srrrg.apiKeyPrincipal")` | RFC 7807 `ProblemDetail` | 면제 |
| `/api/links/**`, `/{code}` | 없음 (+ `X-Srrrg-Secret-Key`) | 없음 | `ApiErrorResponse` | 면제 |

서비스 계층도 쌍으로 존재한다 — `rename()`은 `ProjectService.requireRole`로 사용자 권한을,
`renameForApiKey()`는 API 키의 `projectId`로 소유만 확인한다. 한쪽만 고치지 않는다.

**스키마는 Flyway가 소유한다.** `ddl-auto: validate`, `open-in-view: false`.
이미 적용된 migration은 수정하지 않고 새 버전을 추가한다.

**토큰 원문은 저장하지 않는다.** secret key, API key, refresh token, 초대 토큰, OAuth 연결 토큰
전부 SHA-256 해시만 DB에 넣고 원문은 발급 응답이나 쿠키로 한 번만 나간다.

**`users.email`은 OAuth 공급자가 검증한 주소만 담는다.** 사용자가 입력할 경로가 없다.
계정 연결 판정(`findByEmail`)의 기준이라, 자기 신고 값이 섞이면 이메일만 알면 남의 계정에 붙을 수 있다.

## 프론트엔드

- **새 의존성을 넣지 않는다.** 라이브러리로 8줄을 대체하지 않는다. 프레임워크도 번들러도 없다.
- 화면 CSS는 `base.css`의 토큰만 쓰고 색·크기·굵기를 직접 적지 않는다.
- 굵기는 400 본문 / 500 데이터 값 / 600 라벨·버튼 / 700 제목 네 단계뿐. 700을 넘지 않는다.
- 브랜드 블루는 네 역할에만 — 주 행동, 링크, 포커스, 선택 상태. 보조 버튼은 중립이다.
- 구조 표면(`.surface-panel`)에 그림자를 넣지 않는다. 그림자는 실제로 떠 있는 레이어에만.
- 프로젝트 식별은 `?projectId=...` 쿼리 파라미터다. path variable이 아니다
  (v3 IA 문서가 제안했지만 구현은 기존 관례를 따랐다).
- 새 컴포넌트는 만들기 전에 저장소에 같은 패턴이 있는지 본다. 탭·드로어·모달·팝오버는 이미 있다.

## 문서 권위 순서

같은 주제를 다루는 문서가 여러 세대 쌓여 있다. **최신이 이긴다.**

| 주제 | 현재 기준 | 대체됨 |
|---|---|---|
| 코드·커밋·DB·테스트 컨벤션 | `docs/conventions.md` | — |
| 엔드포인트 호출 흐름 | `docs/flow/*.md` | — |
| 시각 토큰·컴포넌트 | `docs/design-system.md` | `docs/design/design-conventions.md`, `design/v2/02`, `design/v3/03` |
| 화면 정보구조·탭 | `docs/ia-restructure.md` | `design/v3/02`, `design/v3/04` |
| UI 결정 근거 | `docs/ui-ux-decisions.md` (누적 기록) | — |
| 도메인 구조 | `docs/architecture/campaign_utm_templates.md` → `project_campaign_personalized_links.md` | — |

`docs/design/design-conventions.md`는 "공통 CSS 파일이 없다"를 전제로 쓰였다. 지금은 있다. 읽지 않는다.
`docs/srrrg_v1_*.md`, `docs/implement/*`, `docs/*-audit.md`, `docs/design/v2`, `docs/design/v3`는
당시 상태의 기록이다. 히스토리를 확인할 때만 열고, 현재 규칙의 근거로 쓰지 않는다.

UI를 바꿨으면 결정 근거를 `docs/ui-ux-decisions.md`에 덧붙인다.
엔드포인트 흐름을 바꿨으면 해당 `docs/flow/*.md`를 같이 고친다.

## 함정

- `src/main/resources/templates_old/`는 아무 데서도 참조하지 않는 잔재다. 여기 있는 파일을 고쳐도 반영되지 않는다.
- `docs/performance/results/**`는 k6 원시 로그 3만여 줄이다. 전체 검색 시 제외한다.
- 같은 기능이 웹·API 키 두 경로에 있다. 한쪽만 고치면 다른 쪽이 조용히 어긋난다.
