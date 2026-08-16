# identity — 계정

로그인한 사용자 본인의 계정 정보. 이 도메인은 서비스 계층이 없고 **컨트롤러가 리포지토리와 엔티티를 직접 다룬다** — 권한 검사가 "본인 것만"으로 끝나고 분기가 없어서다.

| Method | Path | 핸들러 | 표면 |
|---|---|---|---|
| GET | `/api/web/account` | `AccountController.get` | web |
| PATCH | `/api/web/account` | `AccountController.update` | web |
| POST | `/api/web/account/onboarding` | `AccountController.completeOnboarding` | web |

세 핸들러 모두 `@AuthenticationPrincipal SrrrgPrincipal`에서 `userId`를 얻으므로 **다른 사용자를 지정할 방법 자체가 없다.** 경로에도 본문에도 userId가 없다.

## User 엔티티

| 필드 | 의미 |
|---|---|
| `email` | nullable. **OAuth 공급자가 검증한 주소만** 담는다. 공급자가 주지 않으면 비어 있고, 사용자가 채울 방법은 없다 |
| `displayName` | 필수, 100자. 없으면 이메일 로컬파트가 기본값 |
| `onboardingCompletedAt` | null이면 `needsOnboarding() == true` |

---

### GET /api/web/account

내 계정 정보와 연결된 OAuth 공급자 목록.

```
AccountController.get(principal)
    @Transactional(readOnly = true)

    user(principal.userId())
        UserRepository.findById(userId)
            → 없으면 IllegalArgumentException (400)
            토큰은 유효한데 사용자가 지워진 상태. 실질적으로 도달하지 않는다.

    response(user)
        OAuthAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)
            연결된 공급자를 가입 순서대로 나열한다. GOOGLE·KAKAO·GITHUB 이름만 노출하고
            공급자 측 식별자나 이메일은 응답에 담지 않는다.

    → AccountResponse(id, email, displayName, providers, createdAt)
```

---

### PATCH /api/web/account

표시 이름 변경. 바꿀 수 있는 건 이것뿐이다.

```
AccountController.update(principal, request)
    @Transactional

    (Bean Validation)
        displayName: @NotBlank @Size(max = 100)
        → MethodArgumentNotValidException (400 INVALID_REQUEST)

    user(principal.userId())

    User.updateDisplayName(displayName.trim())
        엔티티 필드만 바꾼다. 트랜잭션 커밋 시 dirty checking으로 UPDATE가 나가고
        @PreUpdate가 updatedAt을 갱신한다. save() 호출이 없는 이유.

    response(user)
```

**핵심 1가지**

- 이메일은 여기서 바꿀 수 없다. 어디서도 바꿀 수 없다. 계정 연결 판정(`findByEmail`)의 기준이라, 사용자가 쓸 수 있는 값이 섞이는 순간 이메일만 알면 남의 계정에 붙을 수 있게 된다.

---

### POST /api/web/account/onboarding

가입 직후 1회 실행. 표시 이름만 확인받는다.

```
AccountController.completeOnboarding(principal, request)
    @Transactional

    (Bean Validation)
        displayName: @NotBlank @Size(max = 100)

    user(principal.userId())

    User.completeOnboarding(displayName.trim())
        displayName을 덮어쓰고 onboardingCompletedAt에 현재 시각을 찍는다.

    response(user)
```

**핵심 2가지**

- **`onboardingCompletedAt`은 로그인 리다이렉트를 결정한다.** `OAuthLoginSuccessHandler.destination()`이 `needsOnboarding()`을 보고 `/onboarding`으로 보낼지 원래 목적지로 보낼지 정한다.
- **여기서 이메일을 받지 않는다.** 예전에는 공급자가 이메일을 주지 않은 사용자에게 직접 입력받았지만, 그 값은 미검증이라 어떤 판정에도 쓸 수 없었고 실제로 쓰이는 곳도 없었다. 필수 입력만 남아 있어 지웠다. 카카오 전용 계정은 이메일이 없는 상태로 남는다.
