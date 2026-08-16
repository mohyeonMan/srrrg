# 페이지와 인프라 엔드포인트

로직이 거의 없는 경로들. Thymeleaf 뷰 이름만 반환하고 실제 데이터는 화면의 JS가 `/api/web/**`를 호출해 채운다.

| Method | Path | 핸들러 | 뷰 |
|---|---|---|---|
| GET | `/` | `HomeController.home` | `index` |
| GET | `/manage` | `HomeController.management` | `management` |
| GET | `/projects` | `HomeController.projects` | `projects` |
| GET | `/account` | `HomeController.account` | `account` |
| GET | `/onboarding` | `HomeController.onboarding` | `onboarding` |
| GET | `/test/utm` | `HomeController.utmTest` | `utm-test` |
| GET | `/favicon.ico` | `HomeController.favicon` | — (204) |
| GET | `/docs/api` | `ApiDocumentationController.documentation` | `api-docs` |
| GET | `/openapi.json` | `ApiDocumentationController.openApi` | forward |
| GET | `/actuator/health` | Spring Boot Actuator | — |
| GET | `/actuator/prometheus` | Spring Boot Actuator | — |

`/login`은 [01-auth.md](01-auth.md), `/invitations/{token}`은 [04-project-member.md](04-project-member.md)에 있다.

## 모든 페이지가 공유하는 것

```
WebAccountModel.account(principal, request, model)
    @ControllerAdvice + @ModelAttribute — 모든 Thymeleaf 모델에 자동 주입된다.

    sessionUserId(principal, request)

        [SecurityContext에 principal이 있으면] 그 userId.

        [없으면] JwtAuthenticationFilter.cookie(request, "srrrg_access")

            JwtService.readSubjectAllowingExpired(token)
                서명·issuer·audience·jti는 그대로 검증하고 만료만 무시한다.
                액세스 토큰(5분)이 만료돼도 리프레시 토큰으로 세션은 살아 있는데,
                페이지를 새로 열면 서버가 이를 알 수 없어 헤더만 로그아웃으로 보이던 문제를 위한 경로.
                표시 전용이며 권한은 부여하지 않는다 — 인증은 계속 JwtService.verify만 쓴다.
                → 실패 시 빈 값

    UserRepository.findById(userId)
        model.addAttribute("accountUser", AccountUser(displayName, email))

    model.addAttribute("accountReturnTo", request.getRequestURI())
        헤더의 로그인 링크가 현재 경로로 되돌아오게 하는 값.
```

**핵심 1가지**

- **`@ExceptionHandler`가 렌더하는 뷰에는 이 어드바이스가 적용되지 않는다.** 그래서 `RedirectExceptionHandler`가 `WebAccountModel.apply(request, model)`를 직접 호출한다. `apply`는 `@AuthenticationPrincipal` 주입을 받을 수 없어 `SecurityContextHolder`에서 직접 꺼낸다.

---

### GET /

랜딩. 익명 링크 생성 폼이 있다.

```
HomeController.home()
    → "index"
```

---

### GET /manage

익명 링크 관리 화면. secret key로 링크를 조회·수정·삭제한다.

```
HomeController.management(code, model)
    model.addAttribute("prefilledCode", code == null ? "" : code)
        생성 직후 ?code=aB3xY9 로 넘어오면 입력란을 미리 채운다.

    → "management"
```

**핵심 1가지**

- **경로 `"manage"`가 단축 코드 예약어다.** 정확히 6자라 `/{code:[0-9A-Za-z]{6}}`에 매칭될 수 있어, `LinkCodeGenerator.RESERVED_CODES`가 이 문자열만 제외한다. 다른 페이지 경로는 전부 6자가 아니라 충돌하지 않는다.

---

### GET /projects

프로젝트 화면. **탭 전환도 이 한 라우트에서 처리한다.**

```
HomeController.projects()
    → "projects"
       실제 화면은 /projects?projectId=..&tab=.. 형태로 진입한다.
```

**핵심 1가지**

- **하위 화면에 단독 라우트를 두지 않았다.** 코드 주석이 이유를 밝힌다 — `project-members`, `project-settings`, `project-utm-templates`, `project-api-keys`, `campaigns`, `statistics`는 `projects.html` 안의 조각이라 단독 라우트로 들어오면 프로젝트 컨텍스트(레일·projectId) 없이 렌더된다.
- 프로젝트 탭은 `overview`, `members`, `utm`, `api`, `settings`다. 기본값은 `overview`이며 `api`는 OWNER에게만 노출된다. 캠페인·링크 상세은 각각 `campaignId`, `linkCode` 파라미터로 같은 셸 안에서 연다.

---

### GET /account · GET /onboarding

```
HomeController.account()      → "account"
HomeController.onboarding()   → "onboarding"
```

둘 다 인증을 요구하지 않는다(`anyRequest().permitAll()`). 화면의 JS가 `/api/web/account`를 호출할 때 401을 받아 로그인으로 유도한다.

`/onboarding`은 로그인 직후 `OAuthLoginSuccessHandler.destination()`이 `user.needsOnboarding()`을 보고 보내는 곳이다.

---

### GET /test/utm

UTM 병합 동작을 눈으로 확인하는 개발용 화면.

```
HomeController.utmTest()
    → "utm-test"
```

**핵심 1가지**

- 운영에서도 접근 가능하다. 인증도 프로파일 조건도 없다.

---

### GET /favicon.ico

```
HomeController.favicon()
    @ResponseStatus(HttpStatus.NO_CONTENT)
    본문이 없는 void 메소드. → 204
```

**핵심 1가지**

- **이 매핑이 없으면 요청이 `/{code:...}`로 흘러가지 않는다** — `favicon.ico`는 11자라 6자 정규식에 안 맞는다. 다만 매핑이 없으면 `NoResourceFoundException` → `GlobalExceptionHandler`가 404 JSON을 만들고, 브라우저가 매 페이지마다 이 실패를 로그에 남긴다. 204를 명시해 그 소음을 없앤 것.

---

### GET /docs/api

사람이 읽는 공개 API 문서.

```
ApiDocumentationController.documentation()
    → "api-docs"
       인증과 scope, 공통 규칙, 엔드포인트 목록, 오류 형식, 요청 한도를
       정적 HTML로 설명한다.
```

**핵심 2가지**

- **OpenAPI JSON을 화면에 그대로 렌더하지 않는다.** `/docs/api`는 사용 순서와 의미를 설명하고, 정확한 요청·응답 schema가 필요할 때만 `/openapi.json`을 연다.
- 별도 문서용 JS나 Swagger UI를 쓰지 않는다. 전역 헤더의 `API` 링크로 진입하며, 랜딩 페이지에 같은 링크를 중복 배치하지 않는다.

---

### GET /openapi.json

OpenAPI 스펙. springdoc의 경로를 안정적인 이름으로 감싼 것이다.

```
ApiDocumentationController.openApi()
    → "forward:/v3/api-docs/public"
       리다이렉트가 아니라 서버 내부 포워드다. 클라이언트에는 /openapi.json으로만 보인다.

OpenApiConfig.publicOpenApi()
    GroupedOpenApi "public" — pathsToMatch: /api/links/**, /api/v1/**
    /api/web/** 는 포함되지 않는다. 브라우저 전용 API라 외부에 공개할 이유가 없다.

OpenApiConfig.srrrgOpenApi()
    title "srrrg API", version v1.
    securityScheme "projectApiKey" — HTTP bearer, bearerFormat "API key".
    공개 API 컨트롤러의 @SecurityRequirement(name = "projectApiKey")가 이 정의를 참조한다.
```

**핵심 1가지**

- **Swagger UI는 기본 비활성이다** (`springdoc.swagger-ui.enabled: ${SRRRG_SWAGGER_UI_ENABLED:false}`). 문서는 자체 제작한 `/docs/api` 화면으로 제공한다.

---

### GET /actuator/health · GET /actuator/prometheus

```
application.yaml
    management.endpoints.web.exposure.include: health,prometheus
        이 둘만 노출한다. 나머지 actuator 엔드포인트는 전부 닫혀 있다.

    management.endpoint.health.probes.enabled: true
        /actuator/health/liveness 와 /actuator/health/readiness 하위 경로를 켠다.
        k3s Deployment의 livenessProbe / readinessProbe가 쓰는 경로.
```

`server.shutdown: graceful`과 함께 롤링 업데이트 중 요청 유실을 막는 구성이다.

**핵심 1가지**

- **인증이 걸려 있지 않다.** `anyRequest().permitAll()`이라 `/actuator/prometheus`가 클러스터 밖에서도 열려 있다면 지표가 그대로 노출된다. 차단은 Ingress 쪽 설정에 달려 있고, 그 매니페스트는 이 레포에 없다.
