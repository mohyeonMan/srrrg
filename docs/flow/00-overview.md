# srrrg 엔드포인트 흐름 문서 — 개요

이 문서 모음은 **요청이 들어와서 응답이 나가기까지 어떤 클래스의 어떤 메소드가 순서대로 호출되는가**를 기록한다.

요청/응답 JSON 스키마는 springdoc이 `/v3/api-docs/public`에 생성하므로 여기서는 다루지 않는다. 여기 담긴 것은 **호출 순서와 각 메소드의 역할**이다.

## 읽는 법

들여쓰기 깊이 = 호출 깊이. 각 메소드 아래 1~2줄로 역할을 적는다.

```
ClassName.methodName(args)
    이 메소드가 무엇을 하는지 한 줄. 왜 이렇게 돼 있는지 한 줄(있을 때만).
    → 실패 시 ExceptionName (HTTP 상태)

    NextClass.nextMethod(args)
        한 단계 안쪽 호출.

        Repository.query()
            쿼리가 하는 일. fetch join·잠금 같은 의도.
```

`→`로 시작하는 줄은 그 지점에서 흐름이 끊기고 오류 응답으로 빠지는 경로다.

## 도메인 지도

| 문서 | 범위 | 엔드포인트 |
|---|---|---|
| [01-auth.md](01-auth.md) | OAuth 로그인, 세션 토큰, 인증 필터 | 6 |
| [02-identity.md](02-identity.md) | 계정 조회·수정·온보딩 | 3 |
| [03-project.md](03-project.md) | 프로젝트 CRUD, 개요, 서브도메인 | 10 |
| [04-project-member.md](04-project-member.md) | 멤버, 초대 | 9 |
| [05-project-apikey.md](05-project-apikey.md) | API 키 발급·조회·폐기 | 3 |
| [06-link.md](06-link.md) | 리다이렉트, 익명 링크, 프로젝트 링크 | 12 |
| [07-campaign.md](07-campaign.md) | 캠페인, UTM 기본값, 캠페인 링크 | 21 |
| [08-utm-template.md](08-utm-template.md) | UTM 템플릿·필드 | 14 |
| [09-campaign-import.md](09-campaign-import.md) | CSV 가져오기·내보내기, 비동기 워커 | 10 |
| [10-statistics.md](10-statistics.md) | 통계 7종 | 7 |
| [11-pages.md](11-pages.md) | Thymeleaf 페이지, API 문서, actuator | 11 |

합계 106행 = 컨트롤러 핸들러 매핑 **102개** + Spring이 제공하는 OAuth 경로 2개 + actuator 2개.

## 시스템 전제

- Java 21 / Spring Boot 4 / Spring MVC (서블릿, WebFlux 아님)
- JPA + PostgreSQL, 스키마는 Flyway가 소유 (`ddl-auto: validate`, `open-in-view: false`)
- Redis — 레이트리밋 카운터
- Thymeleaf SSR + 번들러 없는 바닐라 JS
- **k3s Deployment로 배포되며 파드가 여럿이다.** 매니페스트는 별도 infra 레포(kustomize)에 있고, CI는 이미지 태그만 갱신한다.

멀티 파드 전제가 코드에 직접 드러나는 곳:

- 세션이 STATELESS JWT라 sticky session이 필요 없다
- 레이트리밋 카운터가 Redis에 있다 (인메모리면 파드 수만큼 한도가 뻥튀기된다)
- 동시성 제어가 전부 DB 수준이다 (`FOR UPDATE`, `SKIP LOCKED`, lease) — JVM 락으로는 파드를 가로지를 수 없다
- CSV 워커 `@Scheduled`가 모든 파드에서 돌지만 lease로 중복 처리를 막는다

## 세 개의 API 표면

같은 기능이 표면마다 별도 컨트롤러로 존재한다. 차이는 **주체를 어디서 얻는가**와 **오류 형식**뿐이다.

| 표면 | 인증 | 주체 획득 | 오류 형식 | CSRF |
|---|---|---|---|---|
| `/api/web/**` | 쿠키 JWT | `@AuthenticationPrincipal SrrrgPrincipal` | `ApiErrorResponse` `{code, message}` | 적용 |
| `/api/v1/**` | `Authorization: Bearer srrrg_pk_...` | `request.getAttribute("srrrg.apiKeyPrincipal")` | RFC 7807 `ProblemDetail` | 면제 |
| `/api/links/**`, `/{code}` | 없음 (+ `X-Srrrg-Secret-Key`) | 없음 | `ApiErrorResponse` | 면제 |

서비스 계층도 이 이중 구조를 그대로 반영한다 — `CampaignService.rename()` / `renameForApiKey()` 처럼 대부분의 연산이 쌍으로 존재한다. 앞의 것은 `ProjectService.requireRole`로 사용자 권한을 확인하고, 뒤의 것은 API 키의 `projectId`로 소유 여부만 확인한다.

## 공통 필터 체인

모든 요청이 지나간다. `SecurityConfiguration.securityFilterChain()` 에서 조립된다.

```
SecurityConfiguration.securityFilterChain(http, ...)
    세션 정책 STATELESS, requestCache·formLogin·httpBasic 전부 비활성.
    CSRF는 SPA 모드(쿠키 XSRF-TOKEN / 헤더 X-XSRF-TOKEN, SameSite=Lax)로 두되
    /api/links/**, /api/v1/** 은 제외한다.

    JwtAuthenticationFilter.doFilterInternal(request, response, chain)
        /api/v1/ 로 시작하면 아무것도 하지 않고 통과시킨다. 그 아래는 API 키 필터의 영역.
        액세스 토큰 쿠키를 읽어 SecurityContext에 SrrrgPrincipal을 채운다.

        JwtService.verify(token)
            HS256 서명, issuer, audience(srrrg-web), 만료, 발급시각(미래 60초 허용), jti를 검증한다.
            active kid와 previous kid를 함께 허용해 키 로테이션 중에도 기존 토큰이 살아 있다.
            → 실패 시 IllegalArgumentException

        (검증 실패 처리)
            SecurityContext를 비우기만 하고 요청을 거부하지는 않는다.
            리다이렉트나 공개 페이지처럼 인증이 선택인 경로가 많기 때문.

    ApiKeyAuthenticationFilter.doFilterInternal(request, response, chain)
        shouldNotFilter()가 /api/v1/ 외 전부를 걸러내므로 공개 API에서만 동작한다.
        JWT 필터와 달리 여기서는 인증 실패가 곧 요청 거부다.

        (Authorization 헤더 확인)
            "Bearer srrrg_pk_" 접두사가 없으면 즉시 응답을 끝낸다.
            → ApiProblemWriter.write(401, API_KEY_INVALID)

        ApiKeyService.authenticate(rawKey)
            키를 해시해 조회하고 폐기·만료 여부를 확인한다.
            → ApiKeyUnauthorizedException 시 401 API_KEY_INVALID

        RateLimitService.checkApiKeyRead(keyId)      [GET 요청만]
            분당 300회. 쓰기 요청의 한도는 각 컨트롤러가 직접 건다.
            → 429 + Retry-After

        (요청 속성 주입)
            X-Request-Id 응답 헤더를 붙이고 srrrg.apiKeyPrincipal 속성에 주체를 담는다.
            공개 API 컨트롤러들이 @AuthenticationPrincipal 대신 이 속성을 읽는다.

    CsrfFilter (Spring 제공)
        GET·HEAD·OPTIONS는 면제. 나머지는 X-XSRF-TOKEN 헤더를 요구한다.

    CsrfCookieFilter.doFilterInternal(...)
        지연 생성되는 CSRF 토큰을 강제로 렌더해 쿠키가 실제로 내려가게 한다.

    (인가 매트릭스)
        /api/web/auth/**, /invitations/**  → permitAll
        /api/v1/**                         → permitAll (실제 관문은 API 키 필터)
        /api/web/**                        → authenticated
        그 외                              → permitAll
        → 미인증 401 AUTHENTICATION_REQUIRED / 권한없음 403 ACCESS_DENIED (JSON 직접 기록)
```

## 공통 응답·예외 처리

```
NoStoreResponseAdvice.beforeBodyWrite(...)
    supports()가 항상 true라 모든 JSON 응답에 Cache-Control: no-store를 붙인다.
    관리 API 응답이 브라우저나 중간 캐시에 남지 않게 하기 위함.

WebAccountModel (@ModelAttribute advice)
    모든 Thymeleaf 모델에 accountUser·accountReturnTo를 주입한다.
    표시 전용으로 만료된 토큰도 읽는다(JwtService.readSubjectAllowingExpired).
    단, 예외 핸들러가 렌더하는 뷰에는 적용되지 않아 해당 핸들러들이 apply()를 직접 부른다.

GlobalExceptionHandler (@RestControllerAdvice, 전역)
    도메인 예외를 ApiErrorResponse {code, message} 로 변환한다. 마지막에 catch-all이 있다.

PublicCampaignApiExceptionHandler (@RestControllerAdvice, 공개 캠페인 API 한정)
    같은 예외를 RFC 7807 ProblemDetail로 변환한다. X-Request-Id를 함께 내려준다.

RedirectExceptionHandler (@ControllerAdvice, RedirectController 한정, HIGHEST_PRECEDENCE)
    JSON 대신 redirect-error.html을 렌더한다. 브라우저 사용자용.
```

### 예외 → HTTP 상태 매핑

| 예외 | web (`ApiErrorResponse`) | v1 (`ProblemDetail`) |
|---|---|---|
| `MethodArgumentNotValidException`, `IllegalArgumentException` | 400 `INVALID_REQUEST` | 400 `INVALID_REQUEST` |
| `HttpMessageNotReadableException` | 400 `INVALID_REQUEST` | 400 `INVALID_REQUEST` |
| `MissingRequestHeaderException` | 400 (secret key 헤더 안내) | — |
| `SecurityException` | 403 `PROJECT_ACCESS_DENIED` | 403 `PROJECT_ACCESS_DENIED` |
| `LinkNotFoundException` | 404 `LINK_NOT_FOUND` | — |
| `CampaignNotFoundException` | 404 `CAMPAIGN_NOT_FOUND` | 404 `CAMPAIGN_NOT_FOUND` |
| `NoResourceFoundException` | 404 `NOT_FOUND` | — |
| `LinkGoneException` | 410 `LINK_GONE` | 410 `LINK_GONE` |
| `LinkCodeConflictException` | 409 `LINK_CODE_CONFLICT` | — |
| `ExternalIdConflictException` | 409 `EXTERNAL_ID_CONFLICT` | 409 `EXTERNAL_ID_CONFLICT` |
| `BatchIdempotencyConflictException` | 409 `IDEMPOTENCY_CONFLICT` | 409 `IDEMPOTENCY_CONFLICT` |
| `CampaignImportIdempotencyConflictException` | 409 `IDEMPOTENCY_CONFLICT` | 409 `IDEMPOTENCY_CONFLICT` |
| `LinkManagementService.IdempotencyConflictException` | — | 409 `IDEMPOTENCY_CONFLICT` |
| `ActiveImportConflictException` | 409 `IMPORT_IN_PROGRESS` | 409 `IMPORT_IN_PROGRESS` |
| `UnsafeUrlException` | 400 `URL_THREAT_DETECTED` | 400 `URL_THREAT_DETECTED` |
| `RateLimitExceededException` | 429 + `Retry-After` | 429 + `Retry-After` |
| `UrlRiskCheckFailedException` | 503 `URL_CHECK_FAILED` | 503 `URL_CHECK_FAILED` |
| 그 외 `Exception` | 500 `INTERNAL_SERVER_ERROR` | — |

`UnsafeUrlException`이 400인 이유: 알려진 위협 URL은 클라이언트가 고칠 수 있는 요청 오류로 본다. 반대로 `UrlRiskCheckFailedException`은 외부 검사기가 응답하지 않는 상태라 재시도 가능한 503으로 낸다.

## 권한 검사의 단일 관문

컨트롤러에는 `@PreAuthorize` 같은 어노테이션이 없다. **권한은 전부 서비스 안에 있다.**

```
ProjectService.requireRole(userId, projectId, minimumRole)
    프로젝트 멤버십을 조회하고 역할이 기준 이상인지 확인한다.
    거의 모든 프로젝트 범위 연산이 이 메소드를 먼저 부른다.
    → SecurityException (403 PROJECT_ACCESS_DENIED)
```

`ProjectRole`은 `OWNER(0) → EDITOR(1) → VIEWER(2)` 순으로 선언돼 있고, 비교는 `role.ordinal() > minimum.ordinal()`이면 거부다. 즉 **ordinal이 작을수록 권한이 크다.** 각 엔드포인트가 요구하는 최소 역할은 해당 도메인 문서의 표에 적혀 있다.

## 삭제는 전부 soft delete다

**"보관(archive)"이라는 개념은 이 서비스에 없다.** 삭제만 있고 그것이 soft delete일 뿐이다. UI 문구도 전부 "삭제"다 (`프로젝트 삭제`, `캠페인 삭제`).

| 대상 | 컬럼 | 메커니즘 | 표시 |
|---|---|---|---|
| Link | `deleted_at` timestamp | Hibernate `@SoftDelete(strategy = TIMESTAMP)` | 삭제된 링크는 조회되지 않음 |
| Campaign | `deleted_at` timestamp | Hibernate `@SoftDelete(strategy = TIMESTAMP)` | 삭제된 캠페인은 조회되지 않음 |
| Project | `deleted_at` timestamp | Hibernate `@SoftDelete(strategy = TIMESTAMP)` | 활성 부모를 fetch join한 조회에서 제외 |

[V37__unify_soft_delete_columns.sql](../../src/main/resources/db/migration/V37__unify_soft_delete_columns.sql)이 세 엔티티의 삭제 표현을 통일한다. 삭제는 `repository.delete(...)` 또는 soft-delete bulk query로 실행되고, Hibernate가 `deleted_at`에 시각을 기록한다. 삭제된 링크는 존재하지 않는 링크와 같이 404로 응답한다.

일부 옛 설계 문서(`docs/design/v3/`)가 캠페인에 대해 `보관됨` 용어와 `보관됨 보기` 토글을 제안하지만, **코드는 그 용어를 채택했다가 되돌렸고 토글도 구현되지 않았다.** 현재 코드 기준으로는 무효한 문서다.

## 레이트리밋 한도

`RateLimitService`가 Redis 카운터로 강제한다. 키 접두사는 `srrrg.rate-limit.key-prefix`(기본 `srrrg:dev:`)이며 dev/prod가 Redis를 공유할 때 이 접두사로 분리된다.

| 메소드 | 대상 | 한도 |
|---|---|---|
| `checkAnonymousLinkCreation(ip)` | IP (SHA-256 해시) | 분당 10회, 일 200회 |
| `checkApiKeyRead(keyId)` | API 키 | 분당 300회 |
| `checkApiKeyWrite(keyId)` | API 키 | 분당 60회 |
| `checkJsonBatch(projectId)` | 프로젝트 | 분당 2회 |
| `checkCsvUpload(projectId)` | 프로젝트 | 시간당 5회 |
| `checkBulkLinkQuota(projectId, n)` | 프로젝트 | 일 50,000 링크 |

## 계측

`SrrrgMetrics`가 Micrometer 타이머를 감싼다. 히스토그램과 SLO 버킷은 `application.yaml`에 정의돼 있다.

| 지표 | 태그 | 붙는 위치 |
|---|---|---|
| `srrrg.redirect` | outcome | `RedirectService.redirect` |
| `srrrg.redirect.write` | phase, outcome | 리다이렉트 중 DB 쓰기 구간 |
| `srrrg.link.create` | outcome | 링크 생성 |
| `srrrg.url.risk.check` | — | URL 위험 검사 |
| URL 위험 캐시 | hit / miss_absent / miss_stale | `UrlRiskVerificationService.verify` |

패턴은 공통이다 — `outcome` 변수를 `"error"`로 초기화하고 `finally`에서 기록하므로, 어떤 예외 경로로 빠져나가도 지표가 반드시 남는다.
