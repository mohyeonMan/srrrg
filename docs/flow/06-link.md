# link — 리다이렉트와 링크 관리

서비스의 본체. 링크는 **소유 방식에 따라 두 종류**다.

| 종류 | `project` | 인증 수단 | URL 위험 검사 |
|---|---|---|---|
| 익명 링크 | `null` | `X-Srrrg-Secret-Key` 헤더 | **한다** |
| 프로젝트 링크 | 프로젝트 참조 | JWT 또는 API 키 | 하지 않는다 |

프로젝트 링크에서 위험 검사를 생략하는 것은 의도된 신뢰 정책이다. 코드 주석이 "신뢰 정책이 바뀌면 `requireNoKnownThreat`를 이 지점에 복구한다"고 복원 지점까지 명시해 두었다.

| Method | Path | 핸들러 | 표면 |
|---|---|---|---|
| GET | `/{code}` | `RedirectController.redirect` | 공개 |
| POST | `/api/links` | `LinkController.create` | 익명 |
| GET | `/api/links/{code}` | `LinkController.getManagedLink` | 익명 + secret key |
| PATCH | `/api/links/{code}` | `LinkController.updateManagedLink` | 익명 + secret key |
| DELETE | `/api/links/{code}` | `LinkController.deleteManagedLink` | 익명 + secret key |
| POST | `/api/web/projects/{projectId}/links` | `ProjectController.createLink` | web (EDITOR) |
| GET | `/api/web/projects/{projectId}/links/{code}` | `ProjectController.link` | web (VIEWER) |
| PATCH | `/api/web/projects/{projectId}/links/{code}` | `ProjectController.updateLink` | web (EDITOR) |
| DELETE | `/api/web/projects/{projectId}/links/{code}` | `ProjectController.deleteLink` | web (EDITOR) |
| POST | `/api/web/projects/{projectId}/links/{code}/claim` | `ProjectController.claimLink` | web (EDITOR) + secret key |
| POST | `/api/v1/projects/{projectId}/links` | `PublicProjectLinkController.create` | v1 (`links:write`) |
| GET | `/api/v1/projects/{projectId}/links` | `PublicProjectLinkController.list` | v1 (`links:read`) |

## 코드 생성

```
LinkCodeGenerator.generate()
    Base62 6자를 뽑는다. RESERVED_CODES에 걸리면 다시 뽑는다.
    예약어는 "manage" 하나뿐이다 — 페이지 라우트 중 정확히 6자인 것이 이것뿐이라
    /{code:[0-9A-Za-z]{6}} 매핑과 충돌할 수 있는 유일한 경로다.
```

충돌은 사전 조회로 막지 않고 **저장을 시도하고 unique 위반을 잡는 방식**이다. 파드가 여럿이라 "조회 후 저장"에는 경합 창이 남기 때문이다. 최대 5회 재시도하며, 모두 실패하면 `IllegalStateException`.

---

### GET /{code}

리다이렉트 핫패스. 인증·인가를 전혀 거치지 않는다.

```
SecurityFilterChain
    JwtAuthenticationFilter      쿠키 읽되, 없어도 통과
    CsrfFilter                   GET 면제
    authorizeHttpRequests        anyRequest().permitAll()

RedirectController.redirect(code, host, request)
    경로변수 정규식 [0-9A-Za-z]{6} 으로 단축코드만 잡는다. /manage 같은 페이지가 안 삼켜지는 이유.
    서비스 결과를 302 + Location + Cache-Control: no-store 로 감싼다.

    ClientRequestInfoResolver.resolve(request)
        IP·Referer·User-Agent를 뽑아 ClientRequestInfo로 만든다.
        IP는 trust-forwarded-headers가 true일 때만 X-Forwarded-For를 믿고, 아니면 remoteAddr.
        Referer·User-Agent는 2048자로 절단한다.

    RedirectService.redirect(host, code, info)
        전체 흐름의 오케스트레이터. 진입부에서 accessedAt을 한 번만 찍어
        만료판정과 기록이 같은 시각을 쓰게 한다.
        outcome 기본값을 "error"로 두고 finally에서 기록해, 어느 경로로 빠져나가도 지표가 남는다.

        ProjectDomainService.resolve(host)
            Host 헤더를 URI로 파싱해 서브도메인을 뽑는다.
            path·query·userInfo가 섞이면 거부(헤더 인젝션 방어). IDN.toASCII + 소문자 정규화.
            → 실패 시 LinkNotFoundException (404)

        findLink(route, code)
            서브도메인 유무에 따라 조회 쿼리를 나눈다. 같은 코드라도 서브도메인이 다르면 다른 링크.

            LinkRepository.findBySubdomainIsNullAndCode(code)        [베이스 도메인]
            LinkRepository.findBySubdomainAndCode(subdomain, code)   [서브도메인]
                @EntityGraph로 project·campaign을 fetch join. 뒤에서 바로 쓰므로 N+1을 미리 제거.
                삭제된 링크는 Link의 @SoftDelete가 조회에서 제외한다.
                → 없으면 LinkNotFoundException (404)

        belongsToDeletedProject(link)
            익명 링크가 아닌데 project fetch join 결과가 null이면 삭제된 프로젝트의 링크다.
            → LinkNotFoundException (404) · 이 경우 접근 이벤트는 남기지 않는다

        Link.isExpiredAt(accessedAt)
            expiresAt이 accessedAt 이후가 아니면 만료. 만료 시각 정각도 만료로 본다.

            recordAccess(link, EXPIRED, info)
                만료 접근은 이벤트로 남긴다. 삭제와 달리 "죽은 링크가 아직 트래픽을 받는지" 볼 수 있게.
                → LinkGoneException(EXPIRED) (410)

        effectiveOriginalUrl(link)
            목적지를 정한다. link.originalUrl 우선, 없으면 campaign.defaultOriginalUrl 상속.
            → 둘 다 없으면 LinkGoneException(NO_DESTINATION) (410)

        DestinationUrlMerger.merge(url, utm)          [익명 링크만]
            원본 query·fragment를 보존하며 UTM을 병합한다.
            이름이 겹치는 기존 파라미터는 UTM이 덮어쓰고, UTM은 필드명 오름차순으로 붙는다.

        UrlValidator.validate(url)
            http/https·2048자·host 필수를 확인하고
            localhost·127.x·10.x·172.16~31.x·192.168.x 등 사설망을 차단(SSRF 방어).
            저장 시점뿐 아니라 리다이렉트 시점에도 다시 돌린다.

        UrlRiskVerificationService.verify(url)        [익명 링크만]
            URL의 SHA-256 해시를 키로 검증 결과를 캐시 조회하고, 없거나 만료면 Safe Browsing에 물어본다.
            실패(UNKNOWN)는 저장하지 않아 다음 요청에서 재시도한다.
            → THREAT:  recordAccess(BLOCKED) 후 UnsafeUrlException (403)
            → UNKNOWN: recordAccess(CHECK_FAILED) 후 UrlRiskCheckFailedException (503)
            프로젝트 링크는 인증된 멤버/API키가 만든 것이라 신뢰하고 이 단계를 건너뛴다.

        completeRedirect(...)                          ── 여기서부터 트랜잭션
            앞 단계는 외부 HTTP 호출을 포함해 TX 밖에서 돌린다. DB 커넥션을 그동안 잡지 않기 위해서.
            대신 검사 시점과 응답 시점 사이의 변경을 이 안에서 다시 확인한다.

            findAvailableLink(route, code, accessedAt)
                링크를 다시 읽어 삭제·만료를 재확인한다. 파드가 여럿이라 DB가 유일한 동기화 지점.

            (URL 동일성 비교)
                검사했던 URL과 현재 URL이 다르면 검사되지 않은 곳으로 보내는 셈이므로 중단한다.
                → record(URL_CHANGED) 후 UrlRiskCheckFailedException (503)

            utmValuesFor(link)                         [캠페인 링크만]

                LinkUtmValueRepository.findEffectiveByLinkId(id)
                    링크별 값이 있으면 그것을, 없으면 캠페인 기본값을 COALESCE로 고른다.
                    삭제된 템플릿 필드(deleted_at)는 제외되므로 필드를 지우면 즉시 반영된다.

            LinkAccessEventRecorder.record(link, accessedAt, REDIRECTED, info, utm)
                클릭 1건을 link_access_events에 적재한다. 통계 도메인이 읽는 원천 데이터.

                UserAgentParser.parse(ua)
                    브라우저·OS·기기타입·봇 여부로 분해한다.

                LinkAccessEventRepository.save(LinkAccessEvent)
                    effective_utm을 jsonb로 스냅샷 저장한다.
                    나중에 캠페인 기본값이 바뀌어도 과거 클릭은 보존된다.

            (반환값 결정)
                익명 링크는 위에서 검사한 문자열을 그대로 돌려준다.
                검사한 URL과 응답 URL이 달라지면 안 되므로.
                프로젝트 링크는 현재 URL에 캠페인 UTM을 병합해 돌려준다.

        SrrrgMetrics.recordRedirect(sample, outcome)
            finally에서 호출. outcome은 redirected / blocked / not_found / gone / check_failed 중 하나.

RedirectExceptionHandler                               ── 위 모든 실패의 종착지
    RedirectController 전용 @ControllerAdvice(HIGHEST_PRECEDENCE).
    GlobalExceptionHandler보다 먼저 잡아 JSON 대신 redirect-error.html을 렌더한다.
    503일 때만 Retry-After: 30을 붙이고 재시도 UI를 켠다.

    WebAccountModel.apply(request, model)
        @ControllerAdvice의 @ModelAttribute는 예외 핸들러가 렌더하는 뷰에 적용되지 않는다.
        직접 부르지 않으면 로그인 상태에서도 헤더가 로그아웃으로 보인다.
```

**핵심 3가지**

- **검사는 TX 밖, 쓰기는 TX 안.** Safe Browsing HTTP 호출 동안 DB 커넥션을 안 잡으려고. 대신 TX 안에서 링크를 다시 읽어 재확인한다.
- **URL이 바뀌었으면 보내지 않는다.** 검사한 문자열과 응답 문자열이 반드시 같아야 한다.
- **위험 검사는 익명 링크만.** 프로젝트 링크는 인증된 생성자를 신뢰한다.

---

### POST /api/links

익명 링크 생성. 로그인 없이 쓸 수 있는 유일한 쓰기 경로다.

```
LinkController.create(request, servletRequest)
    (Bean Validation) originalUrl 등 CreateLinkRequest 제약
    → 201 Created

    ClientRequestInfoResolver.resolve(servletRequest).ipAddress()

    RateLimitService.checkAnonymousLinkCreation(ip)
        IP를 SHA-256으로 해시해 카운터 키로 쓴다. 분당 10회, 일 200회.
        IP가 null이면 "unknown" 버킷을 공유한다.
        → RateLimitExceededException (429 + Retry-After)

    LinkManagementService.create(request)
        @Transactional 없음 — saveWithUniqueCode의 saveAndFlush가 자체 트랜잭션으로 동작한다.

        UrlValidator.validate(request.originalUrl())
            → IllegalArgumentException (400)

        validateExpiration(expiresAt)
            null이거나 현재보다 미래여야 한다.
            → IllegalArgumentException (400)

        requireNoKnownThreat(originalUrl)
            익명 링크는 저장 전에 반드시 검사한다.

            UrlRiskVerificationService.verify(url)
                → THREAT:  UnsafeUrlException (400 URL_THREAT_DETECTED)
                → UNKNOWN: UrlRiskCheckFailedException (503 URL_CHECK_FAILED)

        SecretKeyManager.generate()
            "srrrg_sk_" + 43자 URL-safe 랜덤. DB에는 SHA-256 hex만 저장한다.
            원문은 이 응답에서 단 한 번만 나간다.

        saveWithUniqueCode(originalUrl, secretKeyHash, expiresAt)
            LinkCodeGenerator.generate() → LinkRepository.saveAndFlush()
            unique 위반이면 메트릭 collision을 올리고 다시 뽑는다. 최대 5회.
            → 5회 모두 실패 시 IllegalStateException (500)

        SrrrgMetrics.recordLinkCreate(sample, outcome)
            outcome: created / threat / check_failed / invalid / error

    → CreateLinkResponse(code, shortUrl, secretKey, expiresAt)
```

**핵심 2가지**

- **secret key는 여기서만 볼 수 있다.** 이후 조회·수정·삭제에 이 값이 필요하고, 잃어버리면 복구 수단이 없다.
- **레이트리밋이 컨트롤러에 있다.** 필터가 아니라 핸들러 첫 줄이다. 익명 생성만 IP 기준이고 나머지는 키·프로젝트 기준이라 공통 필터로 묶기 어렵다.

---

### GET /api/links/{code}

익명 링크 조회. secret key가 인증 수단이다.

```
LinkController.getManagedLink(code, secretKey)
    @RequestHeader(SECRET_KEY_HEADER) — 헤더가 없으면 Spring이 먼저 막는다.
    → MissingRequestHeaderException (400 "관리용 secret key 헤더가 필요합니다.")

    LinkManagementService.getManagedLink(code, secretKey)
        @Transactional(readOnly = true)

        findManagedLink(code, secretKey)

            LinkRepository.findByCodeAndProjectIsNull(code)
                프로젝트에 편입된 링크는 이 경로로 접근할 수 없다.
                → LinkNotFoundException (404)

            SecretKeyManager.matches(secretKey, link.getSecretKeyHash())
                형식 검사(접두사·길이·문자셋) 후 MessageDigest.isEqual로 상수시간 비교.
                불일치도 404로 응답한다 — 코드의 존재 여부를 흘리지 않기 위해.
                → LinkNotFoundException (404)

            삭제된 링크는 @SoftDelete 때문에 조회되지 않아 404가 된다.

        toManagementResponse(link, editable = true, shortUrl = null)
```

---

### PATCH /api/links/{code}

익명 링크 수정. URL이 실제로 바뀔 때만 재검사한다.

```
LinkController.updateManagedLink(code, secretKey, request)

    LinkManagementService.updateManagedLink(code, secretKey, request)
        메소드 전체에 @Transactional이 없다. 외부 검사 시간 동안 트랜잭션을 유지하지 않으려고
        검사가 끝난 뒤에야 save()로 저장한다.

        findManagedLink(code, secretKey)

        validateUpdateRequest(request)
            바꿀 값이 하나도 없으면 거부.
            → IllegalArgumentException (400)

        (변경 여부 판정)
            urlChanged = originalUrl 필드가 요청에 있고 && 기존 값과 다름
            UpdateLinkRequest는 "필드 없음"과 "null로 설정"을 구분한다(isXxxPresent).

        UrlValidator.validate(newUrl)                [originalUrl이 요청에 있을 때]
        validateExpiration(newExpiresAt)             [expiresAt이 요청에 있을 때]

        requireNoKnownThreat(newUrl)                 [URL이 실제로 바뀐 경우에만]
            같은 URL을 다시 보냈을 때 불필요한 외부 호출을 하지 않는다.
            → UnsafeUrlException (400) / UrlRiskCheckFailedException (503)

        Link.updateOriginalUrl(url) / Link.updateExpiresAt(instant)
        LinkRepository.save(link)
```

---

### DELETE /api/links/{code}

soft delete.

```
LinkController.deleteManagedLink(code, secretKey)

    LinkManagementService.deleteManagedLink(code, secretKey)
        @Transactional
        findManagedLink(code, secretKey)
        Link.delete()
            deleted 플래그만 세운다. 행과 접근 이벤트는 남는다.

    → DeleteLinkResponse(true)
       204가 아니라 200 + 본문이다.
```

---

### POST /api/web/projects/{projectId}/links

프로젝트 링크 생성.

```
ProjectController.createLink(principal, projectId, request)
    → 201 Created

    ProjectService.createProjectLink(userId, projectId, request)
        @Transactional이 없다 — ProjectAccessService.requireRole과 링크 저장이 별도 트랜잭션에서 돈다.

        ProjectAccessService.requireRole(userId, projectId, EDITOR)
            통과한 ProjectMember에서 project와 user를 함께 얻는다.

        LinkManagementService.createForProject(request, project, createdBy)

            UrlValidator.validate(originalUrl)
            validateExpiration(expiresAt)
            (위험 검사 생략 — 인증된 멤버를 신뢰)

            saveProjectLinkWithUniqueCode(url, expiresAt, project, project.activeSubdomain(), ...)
                Link.subdomain에 프로젝트의 활성 서브도메인을 박아 넣는다.
                꺼져 있으면 null — 베이스 도메인 링크가 된다.
                코드 충돌 시 최대 5회 재시도.
```

---

### GET /api/web/projects/{projectId}/links/{code}

프로젝트 링크 단건.

```
ProjectController.link(principal, projectId, code)

    ProjectService.projectLink(userId, projectId, code)
        @Transactional(readOnly = true)
        ProjectAccessService.requireRole(userId, projectId, VIEWER)

        projectLink(projectId, code)                 [private 헬퍼]
            LinkRepository.findByProjectIdAndCode(projectId, code)
                삭제된 링크는 @SoftDelete가 제외한다.
                → 없으면 LinkNotFoundException (404)

        LinkManagementService.projectManagementResponse(link, editable)
            editable = 내 역할이 VIEWER가 아닌지. 화면이 수정 UI를 켤지 결정하는 값.

            projectShortUrl(link)
                link.subdomain이 있으면 {sub}.{baseHost}/{code}, 없으면 {baseUrl}/{code}.
```

---

### PATCH /api/web/projects/{projectId}/links/{code}

프로젝트 링크 수정.

```
ProjectController.updateLink(principal, projectId, code, request)

    ProjectService.updateProjectLink(userId, projectId, code, request)
        @Transactional
        ProjectAccessService.requireRole(userId, projectId, EDITOR)
        projectLink(projectId, code)

        LinkManagementService.updateProjectLink(link, request)

            validateUpdateRequest(request)
                → IllegalArgumentException (400)

            (빈 URL 허용 조건)
                originalUrl을 빈 값으로 보내는 것은 캠페인 링크일 때만 허용한다.
                캠페인 링크는 목적지를 campaign.defaultOriginalUrl에서 상속할 수 있기 때문.
                단일 프로젝트 링크는 목적지가 없으면 리다이렉트가 410이 되므로 거부한다.
                → IllegalArgumentException (400)

            UrlValidator.validate(newUrl)             [빈 값이 아닐 때]
            (위험 검사 생략 — 멤버를 신뢰)

            Link.updateOriginalUrl / updateExpiresAt
            LinkRepository.save(link)
```

---

### DELETE /api/web/projects/{projectId}/links/{code}

```
ProjectController.deleteLink(principal, projectId, code)
    → 204 No Content

    ProjectService.deleteProjectLink(userId, projectId, code)
        @Transactional
        ProjectAccessService.requireRole(userId, projectId, EDITOR)
        LinkRepository.delete(projectLink(projectId, code))
            Hibernate가 DELETE를 deleted_at UPDATE로 번역한다.
            이미 삭제된 링크는 조회되지 않으므로 다시 삭제하면 404가 된다.
```

---

### POST /api/web/projects/{projectId}/links/{code}/claim

익명으로 만든 링크를 프로젝트로 편입한다. **코드와 통계를 유지한 채 소유권만 옮기는** 유일한 경로다.

```
ProjectController.claimLink(principal, projectId, code, secretKey)
    @RequestHeader("X-Srrrg-Secret-Key")
    → 204 No Content

    ProjectService.importAnonymousLink(userId, projectId, code, secret)
        @Transactional
        ProjectAccessService.requireRole(userId, projectId, EDITOR)

        LinkRepository.lockAnonymousByCode(code)
            @Lock(PESSIMISTIC_WRITE) — 두 프로젝트가 동시에 같은 링크를 편입하지 못하게
            행을 잠근다. 파드가 여럿이라 DB 잠금이 유일한 수단.
            → 없으면 IllegalArgumentException (400)

        (편입 가능 여부 3중 검사)
            이미 프로젝트 소유이거나, secretKeyHash가 없거나, secret이 불일치하면 거부.
            세 경우 모두 같은 메시지("링크를 찾을 수 없습니다")로 응답해 구분을 흘리지 않는다.
            → IllegalArgumentException (400)

        Link.assignToProject(project, createdBy)
            project·createdBy를 채우고 secretKeyHash를 null로 지운다.
            편입 후에는 secret key로 접근할 수 없다 — 관리 주체가 프로젝트 권한으로 바뀐다.
            subdomain은 건드리지 않으므로 베이스 도메인 링크로 남는다.

        LinkRepository.flush()
            → DataIntegrityViolationException → LinkCodeConflictException (409)
               같은 코드가 대상 프로젝트에 이미 있는 경우.
```

**핵심 2가지**

- **코드가 바뀌지 않는다.** 이미 배포한 단축 URL을 그대로 두고 소유권만 옮기는 것이 이 기능의 존재 이유다. 누적 클릭 통계도 링크 ID가 그대로라 함께 따라온다.
- **secret key가 폐기된다.** 편입 후 원래 만든 사람은 secret key로 그 링크를 조회·수정할 수 없다.

---

### POST /api/v1/projects/{projectId}/links

공개 API로 링크 생성. `Idempotency-Key`를 지원한다.

```
ApiKeyAuthenticationFilter (선행)
    ApiKeyService.authenticate(raw) → request 속성 srrrg.apiKeyPrincipal

PublicProjectLinkController.create(servletRequest, projectId, idempotencyKey, request)
    → 201 Created

    principal(request, projectId, LINKS_WRITE)
        request.getAttribute("srrrg.apiKeyPrincipal")
            → 없으면 PublicApiException 401 API_KEY_INVALID
        (키의 projectId와 경로의 projectId 일치 확인)
            → PublicApiException 403 PROJECT_ACCESS_DENIED
        (스코프 확인)
            → PublicApiException 403 SCOPE_REQUIRED

    ProjectService.createProjectLink(apiKeyId, projectId, idempotencyKey, request)
        위의 web용 오버로드와 이름은 같지만 시그니처가 다르다.
        ProjectAccessService.requireRole 대신 API 키의 projectId를 신뢰한다.

        RateLimitService.checkApiKeyWrite(apiKeyId)
            분당 60회. GET에 걸리는 read 한도는 필터가 처리하므로 여기서는 write만.
            → RateLimitExceededException (429)

        validIdempotencyKey(idempotencyKey)
            [A-Za-z0-9._:-]{1,100} 정규식.
            → IllegalArgumentException (400)

        (요청 지문 계산)
            originalUrl + "\n" + expiresAt + "\n" + normalizedName 을 SHA-256.
            같은 키로 다른 내용을 보내는 것을 탐지하는 기준값.

        project(projectId)
            삭제된 프로젝트면 → IllegalArgumentException (400)

        LinkManagementService.createForProject(request, project, null, apiKeyId, key, requestHash)

            findIdempotentLink(apiKeyId, idempotencyKey, requestHash)
                LinkRepository.findByIdempotencyApiKeyIdAndIdempotencyKey(apiKeyId, key)
                    있으면 저장된 requestHash와 비교한다.
                    같으면 기존 링크를 그대로 반환(재시도로 간주)
                    → 다르면 IdempotencyConflictException (409 IDEMPOTENCY_CONFLICT)

            UrlValidator.validate(originalUrl)
            validateExpiration(expiresAt)
            (위험 검사 생략)

            saveProjectLinkWithUniqueCode(...)
                unique 위반 시 findIdempotentLink를 한 번 더 시도한다.
                동시에 같은 Idempotency-Key로 들어온 두 요청 중 진 쪽이
                이긴 쪽의 결과를 돌려받게 하는 장치.

    (예외 재포장)
        IdempotencyConflictException → 409 IDEMPOTENCY_CONFLICT
        UrlRiskCheckFailedException  → 503 URL_CHECK_FAILED
        UnsafeUrlException / IllegalArgumentException → 400 INVALID_REQUEST

PublicProjectLinkController.handle(exception)          [@ExceptionHandler]
    이 컨트롤러 전용. RFC 7807 ProblemDetail + X-Request-Id.
    PublicCampaignApiExceptionHandler와 형식은 같지만 별도 구현이다.
```

**핵심 2가지**

- **멱등성이 API 키 단위다.** `(idempotencyApiKeyId, idempotencyKey)` 조합으로 조회하므로 같은 키 문자열이라도 다른 API 키가 쓰면 별개다.
- **경합도 멱등성 안에서 처리한다.** unique 위반을 잡은 뒤 멱등 조회를 재시도하는 구조라, 동시 요청 둘이 들어와도 링크는 하나만 만들어지고 둘 다 같은 결과를 받는다.

---

### GET /api/v1/projects/{projectId}/links

커서 기반 목록. **서비스 계층을 거치지 않고 컨트롤러가 리포지토리를 직접 호출한다.**

```
PublicProjectLinkController.list(request, projectId, cursor, limit)

    principal(request, projectId, LINKS_READ)
        (필터가 이미 read 레이트리밋을 처리했다)

    (limit 검사)
        1~100.
        → PublicApiException 400 INVALID_REQUEST

    LinkRepository.findByProjectIdAndCampaignIsNullOrderByIdDesc(projectId, PageRequest.of(0, limit + 1))
        [cursor 없음] 첫 페이지.
    LinkRepository.findByProjectIdAndCampaignIsNullAndIdLessThanOrderByIdDesc(projectId, cursor, PageRequest.of(0, limit + 1))
        [cursor 있음] id 내림차순이므로 "cursor보다 작은 id"가 다음 페이지.

        limit + 1 개를 읽는 이유: 다음 페이지 존재 여부를 별도 count 쿼리 없이 판정하기 위해.

    (페이지 절단)
        results.size() > limit 이면 limit개로 자르고 마지막 요소의 id를 nextCursor로 준다.
        아니면 nextCursor = null.

    → LinkPageResponse(items, nextCursor)
```

**핵심 2가지**

- **offset 페이징이 아니다.** `id < cursor` 방식이라 페이지가 깊어져도 성능이 일정하고, 조회 중 링크가 추가돼도 중복·누락이 생기지 않는다.
- **캠페인 링크는 제외된다.** `CampaignIsNull` 조건 때문이며, 캠페인 링크는 `/api/v1/campaigns/{id}/links`에서 조회한다 ([07-campaign.md](07-campaign.md)).
