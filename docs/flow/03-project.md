# project — 프로젝트와 서브도메인

프로젝트는 링크·캠페인·멤버·API 키를 묶는 최상위 단위다. 서브도메인을 선점하면 `{sub}.srrrg.link/{code}` 형태의 단축 URL을 쓸 수 있다.

| Method | Path | 핸들러 | 최소 역할 |
|---|---|---|---|
| GET | `/api/web/projects` | `ProjectController.myProjects` | — |
| POST | `/api/web/projects` | `ProjectController.create` | — |
| GET | `/api/web/projects/{projectId}` | `ProjectController.detail` | VIEWER |
| PATCH | `/api/web/projects/{projectId}` | `ProjectController.rename` | OWNER |
| DELETE | `/api/web/projects/{projectId}` | `ProjectController.deleteProject` | OWNER |
| GET | `/api/web/projects/{projectId}/overview` | `ProjectController.overview` | VIEWER |
| GET | `/api/web/projects/{projectId}/subdomain` | `ProjectController.subdomain` | VIEWER |
| PUT | `/api/web/projects/{projectId}/subdomain` | `ProjectController.claimSubdomain` | OWNER |
| PATCH | `/api/web/projects/{projectId}/subdomain/activation` | `ProjectController.activateSubdomain` | OWNER |
| DELETE | `/api/web/projects/{projectId}/subdomain` | `ProjectController.releaseSubdomain` | OWNER |

같은 컨트롤러의 나머지 엔드포인트는 [04-project-member.md](04-project-member.md), [05-project-apikey.md](05-project-apikey.md), [06-link.md](06-link.md)에 있다.

## 권한의 단일 관문

```
ProjectAccessService.requireRole(userId, projectId, minimum)
    프로젝트 범위 연산의 첫 줄에 거의 항상 등장한다. 통과하면 ProjectMember를 돌려주므로
    호출부가 project·user를 다시 조회하지 않아도 된다.

    ProjectMemberRepository.findActiveByProjectAndUser(projectId, userId)
        멤버십이 없으면 프로젝트의 존재 여부조차 알려주지 않는다.
        project를 fetch join하므로 삭제된 프로젝트의 멤버십도 조회되지 않는다.
        → SecurityException (403 PROJECT_ACCESS_DENIED)

    (역할 비교)
        membership.getRole().ordinal() > minimum.ordinal() 이면 거부.
        → SecurityException
```

`ProjectRole`은 **`OWNER(0) → EDITOR(1) → VIEWER(2)`** 순으로 선언돼 있다. ordinal이 작을수록 권한이 크다. `requireRole(..., VIEWER)`는 멤버 전원 통과, `requireRole(..., OWNER)`는 소유자만 통과한다.

## Project 엔티티

| 필드 | 의미 |
|---|---|
| `subdomain` | 선점한 슬러그. **DB `unique` 제약**이 걸려 있다 |
| `subdomainEnabled` | 선점과 별개로 실제 라우팅 활성 여부 |
| `deleted_at` | Hibernate가 관리하는 soft-delete 시각. 엔티티 필드로 직접 노출하지 않는다 |

`activeSubdomain()`은 `subdomainEnabled`일 때만 값을 준다. **선점과 활성이 분리돼 있다** — 슬러그를 미리 잡아두고 나중에 켤 수 있다.

---

### GET /api/web/projects

내가 속한 프로젝트 목록.

```
ProjectController.myProjects(principal)

    ProjectService.myMemberships(userId)
        @Transactional(readOnly = true)

        ProjectMemberRepository.findActiveByUserId(userId)
            삭제된 프로젝트는 목록에서 제외한다.
            project를 fetch join해 별도 로딩 중 예외도 막는다.
            멤버십을 돌려주므로 각 프로젝트에서의 내 역할이 함께 실린다.

    → List<ProjectResponse>
```

---

### POST /api/web/projects

프로젝트 생성. 서브도메인은 선택이다.

```
ProjectController.create(principal, request)
    (Bean Validation) name: @NotBlank @Size(max = 100)
    → 201 Created, 역할은 항상 OWNER

    ProjectService.create(userId, name, requestedSlug)
        @Transactional

        ProjectMemberRepository.countActiveByUserIdAndRole(userId, OWNER)
            소유 프로젝트 5개 상한. 삭제된 것은 세지 않으므로 지운 뒤 새로 만들 수 있다.
            → IllegalStateException (500) — 이 예외만 GlobalExceptionHandler의 catch-all로 떨어진다

        user(userId)
            UserRepository.findById → 없으면 IllegalArgumentException (400)

        validName(name)
            trim 후 1~100자.

        optionalSubdomain(requestedSlug)          [슬러그를 함께 보냈을 때만]

            normalizedSubdomain(requested)
                소문자화 후 3~63자, [a-z0-9]로 시작·끝나고 중간에 하이픈 허용.
                ProjectDomainService.isReservedSubdomain()으로 예약어(api, www, admin 등 20개) 차단.
                → IllegalArgumentException (400)

            ProjectRepository.existsBySubdomain(normalized)
                선점 여부를 미리 확인한다. 경합은 아래 unique 제약이 최종적으로 막는다.

        ProjectRepository.saveAndFlush(Project.create(...))
            즉시 flush해 unique 위반을 이 시점에 잡는다.
            → DataIntegrityViolationException을 IllegalArgumentException("이미 사용 중인 서브도메인입니다.")로 변환

        ProjectMemberRepository.save(new ProjectMember(project, user, OWNER))
            생성자를 OWNER로 등록한다.

        UtmTemplateService.createDefault(project)
            기본 UTM 템플릿을 함께 만든다. 캠페인을 만들자마자 쓸 수 있게 하는 장치.
```

**핵심 2가지**

- **선점 확인을 두 번 한다.** `existsBySubdomain`은 친절한 오류 메시지를 위한 것이고, 실제 보장은 DB unique 제약 + `saveAndFlush`다. 파드가 여럿이라 애플리케이션 수준 검사만으로는 경합을 막을 수 없다.
- **`ensurePersonalProject(userId)`** 가 OAuth 첫 로그인 시 이 메소드를 `"내 프로젝트"`로 호출한다 ([01-auth.md](01-auth.md) 참고).

---

### GET /api/web/projects/{projectId}

프로젝트 단건 + 내 역할.

```
ProjectController.detail(principal, projectId)

    ProjectService.detail(userId, projectId)
        @Transactional(readOnly = true)
        ProjectAccessService.requireRole(userId, projectId, VIEWER) 를 그대로 반환한다.
        조회 자체가 권한 검사이고, 권한 검사의 부산물이 곧 응답 데이터다.
```

---

### PATCH /api/web/projects/{projectId}

이름 변경.

```
ProjectController.rename(principal, projectId, request)
    (Bean Validation) name: @NotBlank @Size(max = 100)

    ProjectService.rename(userId, projectId, name)
        @Transactional
        ProjectAccessService.requireRole(userId, projectId, OWNER)
        validName(name)
        Project.rename(name)
            dirty checking으로 UPDATE. save() 호출 없음.
```

---

### DELETE /api/web/projects/{projectId}

삭제. soft delete이며 행은 남는다. UI 문구도 "프로젝트 삭제"다.

```
ProjectController.deleteProject(principal, projectId)
    → 204 No Content

    ProjectService.delete(userId, projectId)
        @Transactional
        ProjectAccessService.requireRole(userId, projectId, OWNER)
        ProjectRepository.softDeleteById(projectId)
            Hibernate가 DELETE를 deleted_at UPDATE로 번역한다.
```

**핵심 2가지**

- **삭제가 전 도메인으로 전파된다.** 멤버십·API 키·초대 조회가 활성 프로젝트를 fetch join하므로 삭제된 프로젝트의 접근 권한은 사라진다. 프로젝트 링크도 리다이렉트에서 404가 된다.
- **하위 행은 보존한다.** 프로젝트 행과 링크·캠페인 데이터는 남고, 활성 프로젝트를 요구하는 조회에서만 보이지 않는다.

---

### GET /api/web/projects/{projectId}/overview

프로젝트 화면 첫 진입용. 링크와 캠페인을 한 번에 가져온다.

```
ProjectController.overview(principal, projectId)
    두 서비스를 각각 호출해 합친다. 컨트롤러에서 조합하는 유일한 엔드포인트.

    CampaignService.list(userId, projectId, null, 100)
        캠페인 최대 100개. 커서 없이 첫 페이지만.
        내부적으로 ProjectAccessService.requireRole(VIEWER)를 수행한다.

    ProjectService.projectLinks(userId, projectId)
        @Transactional(readOnly = true)
        ProjectAccessService.requireRole(userId, projectId, VIEWER)

        LinkRepository.findByProjectIdAndCampaignIsNullOrderByIdDesc(projectId)
            캠페인에 속하지 않은 링크만. 캠페인 링크는 캠페인 화면에서 따로 본다.
            페이징이 없어 링크가 많은 프로젝트에서는 전량을 읽는다.

    → ProjectOverviewResponse(links, campaigns)
```

**핵심 1가지**

- `ProjectAccessService.requireRole`이 이 요청에서 두 번 실행된다(캠페인 조회 1회, 링크 조회 1회). 두 서비스가 서로를 모르는 대가다.

---

### GET /api/web/projects/{projectId}/subdomain

현재 서브도메인 상태.

```
ProjectController.subdomain(principal, projectId)

    ProjectService.projectDomain(userId, projectId)
        @Transactional(readOnly = true)
        ProjectAccessService.requireRole(userId, projectId, VIEWER).getProject()

    → SubdomainResponse.from(project)
       선점한 슬러그와 활성 여부를 함께 내려준다.
```

---

### PUT /api/web/projects/{projectId}/subdomain

서브도메인 선점 또는 교체. `PUT`인 이유는 멱등한 전체 치환이기 때문이다.

```
ProjectController.claimSubdomain(principal, projectId, request)

    ProjectService.claimSubdomain(userId, projectId, requestedSubdomain)
        @Transactional
        ProjectAccessService.requireRole(userId, projectId, OWNER)

        normalizedSubdomain(requested)
            형식·길이·예약어 검사. 위 create와 같은 규칙.

        (자기 자신이 아닐 때만 중복 확인)
            같은 값을 다시 보내면 통과시킨다. PUT의 멱등성을 지키기 위해.

            ProjectRepository.existsBySubdomain(subdomain)
                → IllegalArgumentException (400)

        Project.claimSubdomain(subdomain)
            subdomainEnabled는 건드리지 않는다. 교체해도 활성 상태가 유지된다.

        ProjectRepository.saveAndFlush(project)
            → DataIntegrityViolationException → IllegalArgumentException (400)
```

---

### PATCH /api/web/projects/{projectId}/subdomain/activation

선점한 서브도메인의 라우팅을 켜고 끈다.

```
ProjectController.activateSubdomain(principal, projectId, request)
    (Bean Validation) enabled: @NotNull

    ProjectService.setSubdomainEnabled(userId, projectId, enabled)
        @Transactional
        ProjectAccessService.requireRole(userId, projectId, OWNER)

        Project.setSubdomainEnabled(enabled)
            켜려는데 선점한 슬러그가 없으면 거부한다.
            → IllegalStateException (500)
```

**핵심 2가지**

- **활성 플래그는 링크 생성 시점에만 반영된다.** `LinkManagementService.createForProject`가 `project.activeSubdomain()`을 읽어 `Link.subdomain` 컬럼에 박아 넣는다. 꺼져 있으면 새 링크는 `subdomain = null`, 즉 베이스 도메인에 생긴다.
- **이미 만들어진 링크는 끄더라도 계속 동작한다.** `RedirectService.findLink`는 Host 헤더에서 뽑은 슬러그와 `Link.subdomain` 컬럼만 대조하고 `Project.subdomainEnabled`를 확인하지 않는다. 비활성화는 "앞으로 이 서브도메인을 쓰지 않겠다"는 뜻이지 기존 링크를 끊는 스위치가 아니다.

---

### DELETE /api/web/projects/{projectId}/subdomain

선점 해제.

```
ProjectController.releaseSubdomain(principal, projectId)

    ProjectService.releaseSubdomain(userId, projectId)
        @Transactional
        ProjectAccessService.requireRole(userId, projectId, OWNER)

        Project.releaseSubdomain()
            subdomain을 null로, subdomainEnabled를 false로 함께 되돌린다.
            둘 중 하나만 남으면 setSubdomainEnabled의 전제가 깨지므로 한 메소드에서 처리한다.

    → SubdomainResponse (비워진 상태)
```

**핵심 1가지**

- 해제하면 그 슬러그는 즉시 다른 프로젝트가 선점할 수 있다. 기존 링크의 `Link.subdomain` 컬럼 값은 그대로 남아 있으므로, 다른 프로젝트가 같은 슬러그를 잡으면 **그 링크들이 새 프로젝트의 서브도메인에서 조회될 수 있다.** `RedirectService.findLink`가 `subdomain + code`로만 조회하기 때문이다.
