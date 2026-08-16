# campaign — UTM 템플릿

UTM 템플릿은 **프로젝트가 쓸 UTM 파라미터 이름의 집합**이다. 캠페인이 템플릿 하나를 선택하고, 그 템플릿의 활성 필드에만 기본값과 링크별 값을 넣을 수 있다.

두 표면이 완전 대칭이다 — 7개 연산이 web과 v1에 각각 존재한다.

| 연산 | Path (`{base}` = `/api/web` 또는 `/api/v1`) | 필요 권한 |
|---|---|---|
| 생성 | `POST {base}/projects/{projectId}/utm-templates` | EDITOR / `campaigns:write` |
| 목록 | `GET {base}/projects/{projectId}/utm-templates` | VIEWER / `campaigns:read` |
| 단건 | `GET {base}/projects/{projectId}/utm-templates/{templateId}` | VIEWER / `campaigns:read` |
| 이름 변경 | `PATCH {base}/projects/{projectId}/utm-templates/{templateId}` | EDITOR / `campaigns:write` |
| 삭제 | `DELETE {base}/projects/{projectId}/utm-templates/{templateId}` | EDITOR / `campaigns:write` |
| 필드 추가 | `POST {base}/projects/{projectId}/utm-templates/{templateId}/fields` | EDITOR / `campaigns:write` |
| 필드 삭제 | `DELETE {base}/.../fields/{fieldId}` | EDITOR / `campaigns:write` |

컨트롤러는 `UtmTemplateController`(web)와 `PublicUtmTemplateController`(v1) 둘 다 `UtmTemplateService`의 `xxx` / `xxxForApiKey` 쌍을 호출한다. **쌍의 유일한 차이는 `requireRole` 유무**이며, 실제 로직은 `doCreate` / `doRename` / `doDelete` / `doAddField` / `doDeleteField` / `doActiveFields` private 메소드로 공유된다.

## 제약

| 항목 | 규칙 |
|---|---|
| 템플릿 이름 | trim 후 1~100자. 프로젝트 내 unique (DB 제약) |
| 필드 이름 | `^[a-z][a-z0-9_]{1,49}$` — 소문자 시작, 소문자·숫자·밑줄, 2~50자 |
| 활성 필드 수 | 템플릿당 최대 10개 (`MAX_ACTIVE_FIELDS`) |
| 삭제 방식 | 템플릿·필드 모두 `deletedAt` 소프트 삭제 |

프로젝트를 만들면 `UtmTemplateService.createDefault(project)`가 `"기본 템플릿"`을 만들고 `utm_source`, `utm_medium`, `utm_campaign` 세 필드를 채운다 ([03-project.md](03-project.md) 참고).

## 공통 조회 헬퍼

```
UtmTemplateService.template(templateId, projectId)
    거의 모든 연산의 첫 단계.

    UtmTemplateRepository.findByIdAndProjectId(templateId, projectId)
        조회를 프로젝트로 좁혀 다른 프로젝트의 템플릿에 닿을 수 없게 한다.
        → IllegalArgumentException("템플릿을 찾을 수 없습니다.") (400)

    UtmTemplate.isDeleted()
        소프트 삭제된 템플릿도 같은 메시지로 처리한다.
        → IllegalArgumentException (400)
```

---

### 템플릿 생성

`POST {base}/projects/{projectId}/utm-templates`

```
UtmTemplateController.create(principal, projectId, request)
    (Bean Validation) name: @NotBlank @Size(max = 100)
    → 201 Created, activeFields는 빈 목록

    UtmTemplateService.create(userId, projectId, name)
        @Transactional
        requireRole(userId, projectId, EDITOR)
        doCreate(project, name)

PublicUtmTemplateController.create(request, projectId, body)
    principal(request, projectId, CAMPAIGNS_WRITE)
        → 401 API_KEY_INVALID / 403 PROJECT_ACCESS_DENIED / 403 SCOPE_REQUIRED

    UtmTemplateService.createForApiKey(projectId, name)
        @Transactional

        ProjectRepository.findById(projectId)
            프로젝트 삭제 여부를 확인하지 않는다.
            → IllegalArgumentException (400)

        doCreate(project, name)

── 공통 ──

    UtmTemplateService.doCreate(project, name)

        validName(name)
            trim 후 1~100자.
            → IllegalArgumentException (400)

        UtmTemplateRepository.saveAndFlush(UtmTemplate.create(project, name))
            즉시 flush해 (projectId, name) unique 위반을 여기서 잡는다.
            → DataIntegrityViolationException → IllegalArgumentException("이미 사용 중인 템플릿 이름입니다.") (400)
```

**핵심 1가지**

- **생성 직후에는 필드가 하나도 없다.** 필드 없는 템플릿을 캠페인에 선택하면 UTM 값을 아무것도 받을 수 없다 — `resolveUtmValues`가 활성 필드에 없는 이름을 전부 거부하기 때문. 필드를 따로 추가해야 쓸모가 생긴다.

---

### 템플릿 목록

`GET {base}/projects/{projectId}/utm-templates`

```
UtmTemplateController.list(principal, projectId)

    UtmTemplateService.list(userId, projectId)
        @Transactional(readOnly = true)
        requireRole(userId, projectId, VIEWER)

        UtmTemplateRepository.findByProjectIdAndDeletedAtIsNullOrderByIdDesc(projectId)
            소프트 삭제된 템플릿은 제외. 페이징이 없다 — 템플릿 수가 많지 않다고 본 것.

    [템플릿마다] UtmTemplateService.activeFields(userId, projectId, template.getId())
        각 템플릿의 활성 필드를 개별 조회한다.

        requireRole(userId, projectId, VIEWER)
            템플릿 개수만큼 권한 검사가 반복된다.

        doActiveFields(projectId, templateId)
            template(templateId, projectId)                      ← 템플릿 재조회
            UtmTemplateFieldRepository.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(templateId)

PublicUtmTemplateController.list(request, projectId)
    principal(request, projectId, CAMPAIGNS_READ)
    UtmTemplateService.listForApiKey(projectId)
    [템플릿마다] UtmTemplateService.activeFieldsForApiKey(projectId, template.getId())
```

**핵심 1가지**

- **N+1이다.** 템플릿 N개면 필드 조회 N번 + 템플릿 재조회 N번 + (web은) 권한 검사 N번이 추가로 돈다. 템플릿 수가 작다는 전제에 기댄 구조다.

---

### 템플릿 단건

`GET {base}/projects/{projectId}/utm-templates/{templateId}`

```
UtmTemplateController.get(principal, projectId, templateId)

    UtmTemplateService.get(userId, projectId, templateId)
        @Transactional(readOnly = true)
        requireRole(userId, projectId, VIEWER)
        template(templateId, projectId)

    UtmTemplateService.activeFields(userId, projectId, templateId)
        권한 검사와 템플릿 조회를 다시 한다.

PublicUtmTemplateController.get(request, projectId, templateId)
    principal(request, projectId, CAMPAIGNS_READ)
    UtmTemplateService.getForApiKey(projectId, templateId)
    UtmTemplateService.activeFieldsForApiKey(projectId, templateId)
```

---

### 템플릿 이름 변경

`PATCH {base}/projects/{projectId}/utm-templates/{templateId}`

```
UtmTemplateController.rename(principal, projectId, templateId, request)
    (Bean Validation) name: @NotBlank @Size(max = 100)
    생성과 같은 CreateUtmTemplateRequest를 재사용한다.

    UtmTemplateService.rename(userId, projectId, templateId, name)
        @Transactional
        requireRole(userId, projectId, EDITOR)
        doRename(projectId, templateId, name)

PublicUtmTemplateController.rename(request, projectId, templateId, body)
    principal(request, projectId, CAMPAIGNS_WRITE)
    UtmTemplateService.renameForApiKey(projectId, templateId, name)

── 공통 ──

    UtmTemplateService.doRename(projectId, templateId, name)
        template(templateId, projectId)

        UtmTemplate.rename(validName(name))
        UtmTemplateRepository.flush()
            dirty checking으로 UPDATE를 밀어내 unique 위반을 이 자리에서 잡는다.
            → DataIntegrityViolationException → IllegalArgumentException (400)

    UtmTemplateService.activeFields(...)  로 필드까지 붙여 응답한다.
```

**핵심 1가지**

- **이름을 바꿔도 링크·기본값에는 영향이 없다.** `link_utm_values`와 `campaign_utm_defaults`가 참조하는 것은 템플릿 이름이 아니라 **필드 이름**이기 때문이다.

---

### 템플릿 삭제

`DELETE {base}/projects/{projectId}/utm-templates/{templateId}`

```
UtmTemplateController.delete(principal, projectId, templateId)
    → 204 No Content

    UtmTemplateService.delete(userId, projectId, templateId)
        @Transactional
        requireRole(userId, projectId, EDITOR)
        doDelete(projectId, templateId)

PublicUtmTemplateController.delete(request, projectId, templateId)
    principal(request, projectId, CAMPAIGNS_WRITE)
    UtmTemplateService.deleteForApiKey(projectId, templateId)

── 공통 ──

    UtmTemplateService.doDelete(projectId, templateId)
        template(templateId, projectId)

        CampaignRepository.countByUtmTemplateId(templateId)
            이 템플릿을 쓰는 캠페인이 하나라도 있으면 거부한다.
            캠페인에서 먼저 템플릿을 바꾸라는 안내 메시지를 준다.
            → IllegalArgumentException (400)

        UtmTemplate.delete()
            deletedAt만 찍는다. 필드 행은 그대로 남는다.
```

**핵심 1가지**

- **참조 무결성을 DB FK가 아니라 count 검사로 지킨다.** 사용 중인 템플릿을 지우면 캠페인의 UTM 해석이 통째로 깨지므로, 삭제 전에 사용처가 없음을 확인한다. 이 검사와 캠페인의 템플릿 선택이 동시에 일어나면 빠져나갈 수 있는 경합 창은 남아 있다.

---

### 필드 추가

`POST {base}/projects/{projectId}/utm-templates/{templateId}/fields`

이 도메인에서 **유일하게 행 잠금을 쓰는** 연산이다.

```
UtmTemplateController.addField(principal, projectId, templateId, request)
    (Bean Validation) name: @NotBlank @Size(max = 50)
    → 201 Created

    UtmTemplateService.addField(userId, projectId, templateId, name)
        @Transactional
        requireRole(userId, projectId, EDITOR)
        doAddField(projectId, templateId, name)

PublicUtmTemplateController.addField(request, projectId, templateId, body)
    principal(request, projectId, CAMPAIGNS_WRITE)
    UtmTemplateService.addFieldForApiKey(projectId, templateId, name)

── 공통 ──

    UtmTemplateService.doAddField(projectId, templateId, name)

        UtmTemplateRepository.lockByIdAndProjectId(templateId, projectId)
            template()과 달리 비관적 잠금으로 템플릿 행을 잡는다.
            아래 10개 상한 검사와 실제 삽입 사이에 다른 파드가 끼어들지 못하게 하기 위함.
            → IllegalArgumentException (400)

        UtmTemplate.isDeleted()
            → IllegalArgumentException (400)

        UtmTemplateFieldRepository.countByUtmTemplateIdAndDeletedAtIsNull(templateId)
            활성 필드 10개 상한.
            → IllegalArgumentException (400)

        validFieldName(name)
            소문자화 후 ^[a-z][a-z0-9_]{1,49}$ 검사.
            "Utm_Source"를 보내면 "utm_source"로 정규화된다.
            → IllegalArgumentException (400)

        UtmTemplateFieldRepository.saveAndFlush(UtmTemplateField.create(template, name))
            → DataIntegrityViolationException → IllegalArgumentException("이미 사용 중인 필드 이름입니다.") (400)
```

**핵심 2가지**

- **잠금이 10개 상한을 지킨다.** 파드가 여럿이라 "카운트 후 삽입" 사이에 경합 창이 생긴다. 템플릿 행을 잠가 같은 템플릿에 대한 필드 추가를 직렬화한다.
- **삭제된 필드 이름은 재사용할 수 있다.** unique 인덱스가 부분 인덱스이기 때문이다 — `uq_utm_template_fields_template_name ON (utm_template_id, name) WHERE deleted_at IS NULL`. 소프트 삭제된 행은 이름을 점유하지 않는다.

---

### 필드 삭제

`DELETE {base}/projects/{projectId}/utm-templates/{templateId}/fields/{fieldId}`

```
UtmTemplateController.deleteField(principal, projectId, templateId, fieldId)
    → 204 No Content

    UtmTemplateService.deleteField(userId, projectId, templateId, fieldId)
        @Transactional
        requireRole(userId, projectId, EDITOR)
        doDeleteField(projectId, templateId, fieldId)

PublicUtmTemplateController.deleteField(request, projectId, templateId, fieldId)
    principal(request, projectId, CAMPAIGNS_WRITE)
    UtmTemplateService.deleteFieldForApiKey(projectId, templateId, fieldId)

── 공통 ──

    UtmTemplateService.doDeleteField(projectId, templateId, fieldId)

        template(templateId, projectId)
            템플릿이 이 프로젝트 것인지 먼저 확인한다.

        UtmTemplateFieldRepository.findByIdAndUtmTemplateId(fieldId, templateId)
            필드가 그 템플릿 소속인지도 확인한다. fieldId는 전역 시퀀스라 필요한 검사.
            → IllegalArgumentException (400)

        UtmTemplateField.delete()
            deletedAt만 찍는다.
```

**핵심 2가지**

- **삭제가 즉시 리다이렉트에 반영된다.** `LinkUtmValueRepository.findEffectiveByLinkId`의 조인 조건에 `field.deleted_at IS NULL`이 있어, 필드를 지우면 그 UTM 파라미터가 **기존 링크의 목적지 URL에서도 즉시 사라진다.**
- **값 행은 지워지지 않으므로 되살릴 수 있다.** `link_utm_values`와 `campaign_utm_defaults`는 필드 ID가 아니라 **필드 이름**으로 조인된다. 필드를 지웠다가 같은 이름으로 다시 추가하면(부분 unique 인덱스라 가능하다) 예전 값들이 그대로 다시 유효해진다.
