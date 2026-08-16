# auth — 로그인과 세션

OAuth로 로그인해 **쿠키에 담긴 JWT 액세스 토큰 + 회전하는 리프레시 토큰**을 발급받는 흐름. 모든 `/api/web/**` 요청의 전제다.

| Method | Path | 핸들러 | 표면 |
|---|---|---|---|
| GET | `/login` | `LoginController.login` | 페이지 |
| GET | `/oauth2/authorization/{provider}` | Spring 제공 | 리다이렉트 |
| GET | `/login/oauth2/code/{provider}` | Spring 제공 → `OAuthLoginSuccessHandler` | 콜백 |
| POST | `/api/web/auth/refresh` | `AuthController.refresh` | web |
| POST | `/api/web/auth/logout` | `AuthController.logout` | web |
| POST | `/api/web/auth/logout-all` | `AuthController.logoutAll` | web |

`provider`는 `google`, `kakao`, `github` 세 가지이며 `ConfiguredClientRegistrationRepository`가 등록한다.

## 쿠키 두 개

| 쿠키 | 내용 | Path | Max-Age |
|---|---|---|---|
| `srrrg_access` | 서명된 JWT (기본 5분 만료) | `/` | 30일 |
| `srrrg_refresh` | `srrrg_rt_` + 43자 랜덤 | `/api/web/auth` | 30일 |

둘 다 `HttpOnly`, `SameSite=Lax`, base-url이 https면 `Secure`.

**액세스 토큰은 5분 만료인데 쿠키 수명은 30일**이다. 토큰 만료에 맞춰 쿠키를 지우면 브라우저가 아무것도 안 보내게 되고, 리프레시 토큰으로 세션이 살아 있는데도 서버가 헤더를 로그아웃으로 그리게 된다. 만료된 토큰은 `JwtAuthenticationFilter`가 어차피 거부하므로 권한에는 영향이 없다.

리프레시 쿠키의 `Path`가 `/api/web/auth`로 좁혀져 있어 일반 API 요청에는 실려 나가지 않는다.

---

### GET /login

로그인 페이지. 세 개의 OAuth 시작 링크를 렌더한다.

```
LoginController.login(returnTo, model)
    returnTo를 모델에 담아 login.html을 렌더한다. 기본값 "/".
    이 값은 OAuth 시작 URL의 쿼리로 전달돼 로그인 후 돌아갈 위치가 된다.
```

---

### GET /oauth2/authorization/{provider}

OAuth 인가 요청 시작. 핸들러는 Spring이 제공하고, 저장소만 커스텀이다.

```
OAuth2AuthorizationRequestRedirectFilter (Spring 제공)

    DefaultOAuth2AuthorizationRequestResolver.resolve(request)
        SecurityConfiguration에서 OAuth2AuthorizationRequestCustomizers.withPkce()를 걸어
        code_verifier / code_challenge(S256)를 생성한다.

    DatabaseAuthorizationRequestRepository.saveAuthorizationRequest(authRequest, request, response)
        state·PKCE·registrationId를 DB에 저장한다. 세션이 STATELESS라 서버 세션에 둘 수 없고,
        파드가 여럿이라 인메모리에 둘 수도 없다.

        OAuthAuthorizationRequestRepository.deleteExpired(now)
            만료된 인가 요청을 먼저 청소한다.

        returnPath(request.getParameter("returnTo"))
            "/"로 시작하고 "//"가 아니며 scheme·host가 없고 1000자 이하일 때만 통과.
            아니면 "/"로 대체한다. 오픈 리다이렉트 방어.

        OAuthAuthorizationRequestRepository.save(...)
            랜덤 43자 토큰을 SHA-256 해시해 PK로 쓰고, 원문은 쿠키로만 내려준다.
            10분 후 만료.

        addCookie(response, "srrrg_oauth_request_" + hash(state)[0:16], raw, 10m)
            쿠키 이름에 state 해시를 붙여, 동시에 여러 탭에서 로그인해도 서로 덮어쓰지 않는다.

    → 302 로 공급자 인가 화면으로 이동
```

---

### GET /login/oauth2/code/{provider}

OAuth 콜백. 이 문서에서 가장 긴 흐름이다.

```
OAuth2LoginAuthenticationFilter (Spring 제공)

    DatabaseAuthorizationRequestRepository.removeAuthorizationRequest(request, response)
        state 쿠키로 저장된 인가 요청을 찾아 복원하고 삭제한다(1회용).
        복원 시 returnPath를 request 속성 RETURN_PATH_ATTRIBUTE에 실어 성공 핸들러로 넘긴다.
        → 없거나 만료면 null → 인증 실패

    (토큰 교환 + 사용자 정보 조회)
        ProviderOAuth2UserService / ProviderOidcUserService 가 공급자별 응답 차이를 흡수한다.

    OAuthLoginSuccessHandler.onAuthenticationSuccess(request, response, authentication)
        여기서부터 srrrg의 로직.

        OAuthProfileFactory.from(oauth)
            공급자별 attribute를 OAuthIdentity(provider, providerUserId, email, emailVerified, name)로 정규화한다.

        OAuthIdentityService.resolve(identity)
            이 OAuth 계정이 누구인지 판정한다. @Transactional.

            normalize(identity)
                providerUserId 필수·255자 이하, 이메일 소문자화·형식 검증(320자, @ 위치),
                표시이름 공백이면 이메일 로컬파트로 대체·100자 절단.
                → 식별자가 없으면 IllegalArgumentException

            OAuthAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                이미 연결된 계정인지 조회한다.

            existingAccount(account, identity)          [연결된 계정이 있을 때]
                account.recordLogin(email, emailVerified) 로 최신 정보를 갱신하고 그대로 로그인시킨다.
                → LoginResolution.authenticated(user)

            newAccount(identity)                        [처음 보는 OAuth 계정일 때]
                검증된 이메일이 없으면 곧바로 새 사용자를 만든다.

                UserRepository.findByEmail(email)
                    같은 이메일을 쓰는 기존 사용자가 있는지 본다.
                    users.email에는 공급자가 검증한 주소만 들어가므로 별도 플래그가 필요 없다.
                    있으면 자동 병합하지 않고 "계정 연결"을 요구한다 — 이메일만 같다고 같은 사람이라 볼 수 없으므로.
                    → LoginResolution.linkRequired(user, identity)

                createUser(identity, verifiedEmail)
                    없으면 User와 OAuthAccount를 새로 만든다.
                    → LoginResolution.authenticated(user)

            ProjectService.ensurePersonalProject(userId)
                연결 대기 상태가 아닐 때만 호출. 개인 프로젝트가 없으면 만들어 준다.
                신규 가입자가 곧바로 링크를 만들 수 있게 하는 장치.

        [분기 A — 계정 연결이 필요한 경우]

            OAuthAccountLinkService.create(existingUser, pendingIdentity, returnPath)
                10분짜리 연결 요청을 DB에 만든다.

                OAuthAccountLinkRequestRepository.deleteExpired(now)
                OAuthAccountLinkRequestRepository.deleteByProviderAndProviderUserId(...)
                    같은 공급자 계정의 이전 대기 요청을 지워 하나만 살아 있게 한다.

                OAuthAccountLinkRequest.create(hash, user, identity, returnPath, expiresAt)
                    토큰은 해시만 저장하고 원문은 쿠키로만 나간다.

            addCookie(response, "srrrg_oauth_link", rawToken, 10m)
            → 302 /login?link_required=true
               사용자는 기존 계정으로 다시 로그인해야 한다.

        [분기 B — 정상 로그인]

            JwtAuthenticationFilter.cookie(request, "srrrg_oauth_link")
                직전에 분기 A를 거친 사용자인지 확인한다.

            OAuthAccountLinkService.complete(pendingToken, user)     [연결 쿠키가 있을 때만]
                만료·소유자 일치·이메일 일치를 모두 확인한 뒤 OAuthAccount를 붙인다.
                요청의 existingUserId와 providerEmail이 지금 로그인한 사용자와 같아야 한다.
                → 불일치·만료 시 IllegalArgumentException → /login?error=oauth
                → 이미 남에게 연결된 계정이면 IllegalStateException
                반환값은 원래 가려던 returnPath.

            WebSessionService.issue(user)
                액세스·리프레시 토큰 한 쌍을 만든다.

                JwtService.requireConfigured()
                    서명 키가 설정돼 있고 base-url이 https(또는 로컬 http)인지 확인한다.
                    운영에서 평문 http로 토큰이 나가는 것을 막는다.

                JwtService.issue(userId)
                    HS256 서명, issuer=base-url, audience=srrrg-web, sub=userId, jti=UUID, 기본 5분 만료.

                RefreshTokenService.issue(user)
                    새 UUID를 tokenFamilyId로 삼아 30일짜리 리프레시 토큰을 만든다.
                    원문은 "srrrg_rt_"+43자, DB에는 SHA-256 해시만 저장.

            WebTokenCookies.write(request, response, tokens)
                두 쿠키를 내려준다.

            OAuthLoginSuccessHandler.destination(user, returnPath)
                user.needsOnboarding()이면 /onboarding?returnTo=... 로, 아니면 returnPath로.

            → 302

        [실패 처리]
            IllegalArgumentException 또는 IllegalStateException 발생 시
            WebTokenCookies.clear() 후 → 302 /login?error=oauth

        [finally]
            OAuth2AuthorizedClientService.removeAuthorizedClient(registrationId, name)
                공급자 액세스 토큰은 로그인 확인 용도로만 쓰고 즉시 버린다. 성공·실패 무관.

    OAuthLoginFailureHandler.onAuthenticationFailure(...)
        위 흐름에 도달하기 전(토큰 교환 실패 등)의 실패를 잡는다.
        오류 코드를 로그에 남기고 → 302 /login?error=oauth
```

**핵심 3가지**

- **인가 요청 상태를 DB에 둔다.** 세션이 STATELESS이고 파드가 여럿이라 서버 세션·인메모리 저장이 불가능하다. 쿠키에는 랜덤 토큰만, DB에는 그 해시만 둔다.
- **같은 이메일이라도 자동 병합하지 않는다.** 다른 공급자로 처음 로그인했을 때 검증된 이메일이 겹치면 계정 연결을 강제한다. 이메일 소유만으로 계정을 탈취할 수 없게 하는 장치.
- **공급자 토큰은 즉시 폐기한다.** srrrg는 로그인 확인만 필요하고 공급자 API를 대신 호출하지 않는다.

---

### POST /api/web/auth/refresh

액세스 토큰 재발급. 리프레시 토큰은 매번 새것으로 교체된다.

```
AuthController.refresh(refreshToken, request, response)
    refresh 쿠키를 읽어 서비스에 넘기고, 새 토큰 쌍을 쿠키로 다시 내려준다.
    → 204 No Content

    WebSessionService.refresh(rawRefreshToken)

        JwtService.requireConfigured()

        RefreshTokenService.rotate(rawToken)
            @Transactional(noRollbackFor = RefreshTokenReuseException.class)
            — 재사용 탐지 시 패밀리 폐기가 롤백되면 안 되므로 예외를 롤백 대상에서 뺐다.

            validateFormat(rawToken)
                "srrrg_rt_" + 43자 URL-safe 문자인지 정규식으로 확인. DB 조회 전에 거른다.
                → InvalidRefreshTokenException

            RefreshTokenRepository.findByHashForUpdate(sha256(raw))
                해시로 조회하며 행을 잠근다. 동시 회전 요청이 같은 토큰을 두 번 쓰지 못하게.
                → 없으면 InvalidRefreshTokenException

            (usedAt != null 검사)
                이미 사용된 토큰이 다시 왔다 = 토큰이 유출됐다는 신호.

                RefreshTokenRepository.revokeFamily(tokenFamilyId, now)
                    같은 패밀리의 모든 토큰을 폐기한다. 공격자와 정상 사용자 양쪽 세션이 함께 끊긴다.
                    → RefreshTokenReuseException

            (revokedAt / 만료 검사)
                → InvalidRefreshTokenException

            create(user, current.getTokenFamilyId())
                같은 패밀리 ID를 유지한 채 새 토큰을 만든다. 패밀리가 유출 추적의 단위.

            current.replaceWith(replacement.id(), now)
                기존 토큰에 usedAt과 후속 토큰 ID를 기록한다.

        JwtService.issue(userId)

    WebTokenCookies.write(request, response, tokens)

AuthController.invalidRefresh(request, response)          [@ExceptionHandler]
    InvalidRefreshTokenException·RefreshTokenReuseException을 잡아
    쿠키를 지우고 → 401 INVALID_REFRESH_TOKEN
```

**핵심 2가지**

- **회전 + 재사용 탐지.** 한 번 쓴 리프레시 토큰이 다시 오면 패밀리 전체를 폐기한다. 유출을 막지는 못하지만 유출 후 지속 사용은 막는다.
- **행 잠금으로 동시 회전을 직렬화한다.** `findByHashForUpdate`가 없으면 두 요청이 동시에 같은 토큰을 회전시켜 둘 다 성공할 수 있다.

---

### POST /api/web/auth/logout

현재 브라우저 세션만 종료.

```
AuthController.logout(refreshToken, request, response)

    RefreshTokenService.logoutCurrent(rawToken)                [쿠키가 있을 때만]
        find(rawToken) → RefreshTokenRepository.findByHashForUpdate(...)
        RefreshTokenRepository.revokeFamily(tokenFamilyId, now)
            그 브라우저의 토큰 패밀리만 폐기한다. 다른 기기의 세션은 남는다.

    (InvalidRefreshTokenException 무시)
        토큰이 이미 무효여도 로그아웃은 성공해야 한다. 쿠키 제거가 본체.

    WebTokenCookies.clear(request, response)
    → 204
```

---

### POST /api/web/auth/logout-all

모든 기기에서 로그아웃.

```
AuthController.logoutAll(refreshToken, request, response)

    RefreshTokenService.logoutAll(rawToken)
        find(rawToken)
        (usedAt·revokedAt·만료 검사)
            logout과 달리 여기서는 유효한 토큰을 요구한다.
            무효한 토큰으로 남의 전체 세션을 끊을 수 없어야 하므로.
            → InvalidRefreshTokenException → 401

        RefreshTokenRepository.revokeAllForUser(userId, now)
            사용자의 모든 패밀리를 폐기한다.

    WebTokenCookies.clear(request, response)
    → 204
```

**핵심 1가지**

- `logout`은 실패를 무시하고 `logout-all`은 유효한 토큰을 요구한다. 전자는 로컬 정리, 후자는 계정 전역 조작이라 요구 수준이 다르다.
