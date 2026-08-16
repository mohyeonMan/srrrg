# project — API 키

`/api/v1/**` 공개 API의 자격 증명. 프로젝트 단위로 발급하며 **OWNER만** 다룰 수 있다.

| Method | Path | 핸들러 | 최소 역할 |
|---|---|---|---|
| GET | `/api/web/projects/{projectId}/api-keys` | `ProjectController.apiKeys` | OWNER |
| POST | `/api/web/projects/{projectId}/api-keys` | `ProjectController.createApiKey` | OWNER |
| DELETE | `/api/web/projects/{projectId}/api-keys/{keyId}` | `ProjectController.revokeApiKey` | OWNER |

## 다른 도메인과 다른 점

`ApiKeyService`는 `ProjectService.requireRole`을 쓰지 않고 **자체 `requireOwner`를 가진다.**

```
ApiKeyService.requireOwner(userId, projectId)
    ProjectMemberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
        → 없으면 SecurityException (403)
    (역할이 정확히 OWNER인지)
        requireRole과 달리 ordinal 비교가 아니라 동등 비교다. 결과는 같지만
        ProjectService에 의존하지 않으려고 별도로 구현돼 있다.
        → SecurityException (403)
```

`requireRole`과 달리 **프로젝트 삭제 여부를 확인하지 않는다.** 삭제된 프로젝트에서도 키 목록 조회·발급·폐기가 가능하다. 다만 그 키로 실제 API를 호출하면 `authenticate`가 삭제 여부를 확인해 막는다.

## 키 형식

```
srrrg_pk_<prefix 8자>_<secret 43자>
```

- `prefix`는 평문으로 DB에 저장돼 화면에서 어떤 키인지 식별하는 데 쓰인다
- 전체 문자열의 SHA-256만 `keyHash`로 저장된다. **원문은 발급 응답에서 단 한 번만 나간다**
- 필터가 `Bearer srrrg_pk_` 접두사로 1차 판별한다 ([00-overview.md](00-overview.md) 참고)

## 스코프

| enum | 값 |
|---|---|
| `LINKS_READ` | `links:read` |
| `LINKS_WRITE` | `links:write` |
| `CAMPAIGNS_READ` | `campaigns:read` |
| `CAMPAIGNS_WRITE` | `campaigns:write` |
| `STATS_READ` | `stats:read` |

스코프 검사는 이 서비스가 아니라 **각 공개 API 컨트롤러가 직접** 한다. `authenticate`는 스코프 집합을 `ApiKeyPrincipal`에 실어 넘기기만 한다.

---

### GET /api/web/projects/{projectId}/api-keys

발급된 키 목록. 해시도 원문도 응답에 포함되지 않는다.

```
ProjectController.apiKeys(principal, projectId)

    ApiKeyService.list(userId, projectId)
        @Transactional(readOnly = true)
        requireOwner(userId, projectId)

        ProjectApiKeyRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            폐기된 키도 포함해 전부 돌려준다. 감사 목적으로 이력을 남기기 위함.

    → List<ApiKeyResponse>
       prefix, 이름, 스코프, 만료·폐기·마지막 사용 시각.
```

---

### POST /api/web/projects/{projectId}/api-keys

키 발급. **원문을 볼 수 있는 유일한 순간이다.**

```
ProjectController.createApiKey(principal, projectId, request)
    (Bean Validation) name: @NotBlank, scopes: @NotEmpty
    → 201 Created

    ApiKeyScope.fromValue(value)                     [컨트롤러에서 문자열 → enum 변환]
        "links:read" 같은 값을 enum으로 바꾼다.
        → 모르는 값이면 IllegalArgumentException (400)

    ApiKeyService.create(userId, projectId, name, scopes, expiresAt)
        @Transactional
        requireOwner(userId, projectId)

        (이름 검사)   trim 후 1~100자      → IllegalArgumentException (400)
        (스코프 검사) 최소 1개              → IllegalArgumentException (400)
        (만료 검사)   null이거나 미래       → IllegalArgumentException (400)

        SecureRandomStringGenerator.generate(CHARS, 8)
            prefix. 영숫자만 쓴다(하이픈·언더스코어 제외) — 구분자 "_"와 섞이지 않게.

        SecureRandomStringGenerator.generate(CHARS, 43)
            secret 본문.

        ProjectApiKey.create(project, name, prefix, hash(raw), user, scopes, expiresAt)
            hash(raw)는 전체 문자열의 SHA-256 hex. 원문은 엔티티에 저장하지 않는다.

        project(projectId) / user(userId)
            → 없으면 IllegalArgumentException (400)
            requireOwner가 이미 멤버십을 확인했으므로 실질적으로 도달하지 않는다.

        ProjectApiKeyRepository.save(key)

    → CreatedApiKeyResponse.from(created)
       rawKey를 포함한다. 이후 어떤 API로도 다시 조회할 수 없다.
```

**핵심 2가지**

- **키 개수 상한이 없다.** 프로젝트 5개 상한이나 UTM 필드 10개 상한과 달리 API 키는 무제한으로 만들 수 있다.
- **`revoke`는 있지만 `update`는 없다.** 이름이나 스코프를 바꾸려면 폐기하고 새로 발급해야 한다.

---

### DELETE /api/web/projects/{projectId}/api-keys/{keyId}

폐기. 행은 남기고 `revokedAt`만 찍는다.

```
ProjectController.revokeApiKey(principal, projectId, keyId)
    → 204 No Content

    ApiKeyService.revoke(userId, projectId, keyId)
        @Transactional
        requireOwner(userId, projectId)

        ProjectApiKeyRepository.findById(keyId)
            → 없으면 IllegalArgumentException (400)

        (소유 프로젝트 확인)
            keyId는 전역 시퀀스라 경로의 projectId와 실제 소유 프로젝트가 다를 수 있다.
            다르면 "찾을 수 없음"으로 처리해 다른 프로젝트의 키 존재 여부를 흘리지 않는다.
            → IllegalArgumentException (400)

        ProjectApiKey.revoke()
            revokedAt을 찍는다. 다음 요청부터 isUsableAt()이 false가 된다.
```

---

## 참고 — 발급된 키가 실제로 쓰이는 경로

키 자체의 CRUD는 web 표면이지만, 검증은 매 `/api/v1/**` 요청마다 일어난다.

```
ApiKeyAuthenticationFilter.doFilterInternal(...)

    ApiKeyService.authenticate(raw)
        @Transactional — recordUse()가 쓰기를 하므로 readOnly가 아니다.

        ProjectApiKeyRepository.findByKeyHash(hash(raw))
            해시로만 조회한다. prefix는 조회에 쓰이지 않는다.
            → ApiKeyUnauthorizedException (401)

        ProjectApiKey.isUsableAt(now)
            폐기·만료 여부.
            → ApiKeyUnauthorizedException (401)

        (프로젝트 삭제 검사)
            삭제된 프로젝트의 키는 쓸 수 없다. 발급·조회와 달리 여기서는 확인한다.
            → ApiKeyUnauthorizedException (401)

        ProjectApiKey.recordUse()
            lastUsedAt을 갱신한다. 모든 공개 API 요청이 이 UPDATE를 유발한다.

    → ApiKeyPrincipal(keyId, projectId, scopes)
```

**핵심 1가지**

- **매 요청이 키 행에 UPDATE를 낸다.** `recordUse()` 때문에 공개 API는 읽기 요청도 쓰기 트랜잭션이 된다. 같은 키로 트래픽이 몰리면 이 행이 경합 지점이 될 수 있다.
