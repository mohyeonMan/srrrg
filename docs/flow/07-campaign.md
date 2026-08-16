# campaign — 캠페인과 캠페인 링크

캠페인은 **UTM 템플릿 하나를 선택하고, 그 필드들의 기본값을 정한 뒤, 링크를 대량으로 찍어내는** 단위다. 링크마다 UTM을 다시 입력하지 않아도 되게 하는 것이 존재 이유다.

이 도메인은 web과 v1 두 표면이 거의 대칭이다. 아래 각 절은 **두 표면을 함께** 다룬다 — 진입점만 다르고 서비스 로직은 공유되거나 `xxxForApiKey` 쌍으로 갈라지기 때문이다.

| 연산 | web (`/api/web`) | v1 (`/api/v1`) | 필요 권한 |
|---|---|---|---|
| 생성 | `POST /projects/{projectId}/campaigns` | 동일 | EDITOR / `campaigns:write` |
| 목록 | `GET /projects/{projectId}/campaigns` | 동일 | VIEWER / `campaigns:read` |
| 단건 | `GET /campaigns/{campaignId}` | 동일 | VIEWER / `campaigns:read` |
| 수정 | `PATCH /campaigns/{campaignId}` | 동일 | EDITOR / `campaigns:write` |
| 삭제 | `DELETE /campaigns/{campaignId}` | **없음** | EDITOR |
| 템플릿 선택 | `PATCH /campaigns/{campaignId}/utm-template` | 동일 | EDITOR / `campaigns:write` |
| 기본값 조회 | `GET /campaigns/{campaignId}/utm-defaults` | 동일 | VIEWER / `campaigns:read` |
| 기본값 수정 | `PATCH /campaigns/{campaignId}/utm-defaults` | 동일 | EDITOR / `campaigns:write` |
| 링크 생성 | `POST /campaigns/{campaignId}/links` | 동일 | EDITOR / `links:write` |
| 링크 일괄 생성 | **없음** | `POST /campaigns/{campaignId}/links/batch` | `links:write` |
| 링크 목록 | `GET /campaigns/{campaignId}/links` | 동일 | VIEWER / `links:read` |
| 링크 일괄 삭제 | `DELETE /campaigns/{campaignId}/links` | **없음** | EDITOR |

CSV 가져오기·내보내기 5종은 [09-campaign-import.md](09-campaign-import.md)에 있다.

## 두 표면이 갈라지는 지점

```
[web]  @AuthenticationPrincipal SrrrgPrincipal → userId
       CampaignService.requireRole(userId, projectId, role)
           ProjectService의 것과 동일한 로직을 CampaignService가 private으로 다시 갖고 있다.
           ProjectService에 의존하지 않기 위한 중복이다.

[v1]   PublicCampaignController.projectIdFrom(request)
           request 속성 srrrg.apiKeyPrincipal에서 projectId를 꺼낸다.
           → 없으면 PublicApiException 401 API_KEY_INVALID

       PublicCampaignController.principal(request, projectId, scope)
           키의 projectId 일치 + 스코프 보유를 확인한다.
           → 403 PROJECT_ACCESS_DENIED / 403 SCOPE_REQUIRED

       CampaignService.findForApiKey(projectId, campaignId)
           CampaignRepository.findByIdAndProjectId(campaignId, projectId)
               조회 자체를 프로젝트로 좁혀 다른 프로젝트의 캠페인에 닿을 수 없게 한다.
               → CampaignNotFoundException (404)
```

**v1에는 `{campaignId}`만 있고 `{projectId}`가 경로에 없다.** 프로젝트는 API 키에서 나오므로 경로에 담을 필요가 없고, 담지 않으니 위조할 수도 없다.

---

### 캠페인 생성

`POST /api/web/projects/{projectId}/campaigns` · `POST /api/v1/projects/{projectId}/campaigns`

```
CampaignController.create(principal, projectId, request)
    (Bean Validation) name: @NotBlank @Size(max=100), description: @Size(max=500),
                      defaultOriginalUrl: @Size(max=2048)
    → 201 Created

    CampaignService.create(userId, projectId, name, description, defaultOriginalUrl)
        @Transactional
        requireRole(userId, projectId, EDITOR)

        validName / validDescription / validDefaultOriginalUrl
            → IllegalArgumentException (400)

        CampaignRepository.save(Campaign.create(project, ..., user(userId)))

PublicCampaignController.create(request, projectId, body)
    principal(request, projectId, CAMPAIGNS_WRITE)

    CampaignService.createForApiKey(projectId, name, description, defaultOriginalUrl)
        @Transactional

        ProjectRepository.findById(projectId)
            requireRole과 달리 프로젝트 삭제 여부를 확인하지 않는다.
            → 없으면 IllegalArgumentException (400)

        CampaignRepository.save(Campaign.create(project, ..., createdBy = null))
            API 키로 만든 캠페인은 createdBy가 null이다.
```

**핵심 2가지**

- **`defaultOriginalUrl` 검증은 형식까지만이다.** `UrlValidator.validate`로 스킴·호스트·사설망을 막지만 URL 위험 검사(`requireNoKnownThreat`)는 생략한다. 코드 주석이 복원 지점을 명시해 둔 의도적 생략이다.
- **v1 생성은 body에 Bean Validation이 걸려 있지 않다.** `@Valid`가 없어 길이 제약은 `CampaignService.validName` 등 서비스 검증으로만 걸린다.

---

### 캠페인 목록

`GET /api/web/projects/{projectId}/campaigns` · `GET /api/v1/projects/{projectId}/campaigns`

```
CampaignController.list(principal, projectId, cursor, limit)

    boundedLimit(limit)
        1~100. 초과 시 web은 IllegalArgumentException(400),
        v1은 PublicApiException(400 INVALID_REQUEST).

    CampaignService.list(userId, projectId, cursor, boundedLimit + 1)
        @Transactional(readOnly = true)
        requireRole(userId, projectId, VIEWER)

        listPage(projectId, cursor, limit)
            CampaignRepository.findByProjectIdOrderByIdDesc(projectId, page)                 [cursor 없음]
            CampaignRepository.findByProjectIdAndIdLessThanOrderByIdDesc(projectId, ..., page) [cursor 있음]
                id 내림차순 커서 페이징. limit+1을 읽어 다음 페이지 존재를 판정한다.

    CampaignPageResponse.of(page, boundedLimit)
        limit개로 자르고 마지막 id를 nextCursor로 준다.

PublicCampaignController.list(request, projectId, cursor, limit)
    principal(request, projectId, CAMPAIGNS_READ)
    CampaignService.listForApiKey(projectId, cursor, limit)
        requireRole만 빠지고 listPage는 동일하다.
```

---

### 캠페인 단건

`GET /api/web/campaigns/{campaignId}` · `GET /api/v1/campaigns/{campaignId}`

```
CampaignController.get(principal, campaignId)

    CampaignService.get(userId, campaignId)
        @Transactional(readOnly = true)

        CampaignRepository.findById(campaignId)
            먼저 캠페인을 찾고 그 프로젝트로 권한을 검사한다. 경로에 projectId가 없기 때문.
            → CampaignNotFoundException (404)

        requireRole(userId, campaign.getProject().getId(), VIEWER)
            → SecurityException (403)

PublicCampaignController.get(request, campaignId)
    projectIdFrom(request)
    CampaignService.findForApiKey(projectId, campaignId)     ← 스코프 검사보다 먼저 실행된다
    principal(request, campaign.getProject().getId(), CAMPAIGNS_READ)
        조회가 이미 키의 projectId로 좁혀져 있어 교차 접근은 불가능하지만,
        스코프가 없는 키도 조회 쿼리 한 번은 유발한다.
```

---

### 캠페인 수정

`PATCH /api/web/campaigns/{campaignId}` · `PATCH /api/v1/campaigns/{campaignId}`

부분 수정. `UpdateCampaignRequest`가 **"필드 없음"과 "null로 설정"을 구분**한다(`isXxxPresent`).

```
CampaignController.update(principal, campaignId, request)

    (변경 항목 존재 확인)
        request.hasChanges()
        → IllegalArgumentException (400)

    CampaignService.rename(userId, campaignId, name)                     [name이 있을 때]
    CampaignService.changeDescription(userId, campaignId, description)   [description이 있을 때]
    CampaignService.changeDefaultOriginalUrl(userId, campaignId, url)    [defaultOriginalUrl이 있을 때]

        각각 @Transactional이며 내부에서

        CampaignService.requireEditableCampaign(userId, campaignId)
            campaignOrNotFound(campaignId) → CampaignNotFoundException (404)
            requireRole(userId, projectId, EDITOR) → SecurityException (403)

        Campaign.rename / changeDescription / changeDefaultOriginalUrl
            dirty checking으로 UPDATE.
```

**핵심 1가지**

- **필드마다 별도 트랜잭션이 열린다.** 세 필드를 한 요청으로 바꾸면 `requireEditableCampaign`이 3번, 트랜잭션이 3번 돈다. 중간에 실패하면 앞의 변경은 이미 커밋된 상태로 남는다 — 부분 적용이 가능하다는 뜻이다.

---

### 캠페인 삭제

`DELETE /api/web/campaigns/{campaignId}` — **web 전용**

```
CampaignController.delete(principal, campaignId)
    → 204 No Content

    CampaignService.delete(userId, campaignId)
        @Transactional
        requireEditableCampaign(userId, campaignId)

        LinkRepository.softDeleteByCampaignId(campaignId)
            @Modifying 벌크 UPDATE. 캠페인의 살아 있는 링크를 전부 deleted = true로.
            링크 행은 남으므로 누적 통계도 남는다.

        CampaignImportRepository.cancelActiveByCampaignId(campaignId)
            PENDING·PROCESSING 상태의 import를 CANCELLED로 바꾸고 lease를 비운다.
            워커가 이미 집어간 import도 다음 행 처리에서 상태를 보고 멈춘다.

        CampaignRepository.delete(campaign)
            캠페인만 하드 삭제한다.
```

**핵심 1가지**

- **캠페인은 하드 삭제, 링크는 소프트 삭제다.** 링크의 `campaign_id`가 가리키던 캠페인이 사라지므로, 삭제된 링크는 목적지 상속(`campaign.defaultOriginalUrl`)도 UTM 기본값도 잃는다. 이미 `deleted = true`라 리다이렉트되지 않으니 문제되지 않는다.

---

### UTM 템플릿 선택

`PATCH /api/web/campaigns/{campaignId}/utm-template` · `PATCH /api/v1/campaigns/{campaignId}/utm-template`

```
CampaignController.selectTemplate(principal, campaignId, request)

    CampaignService.selectTemplate(userId, campaignId, templateId)
        @Transactional
        requireEditableCampaign(userId, campaignId)

        applyTemplateSelection(campaign, templateId)

            [templateId가 null이면] 템플릿 해제. 이후 UTM 값을 받을 수 없게 된다.

            UtmTemplateRepository.findByIdAndProjectId(templateId, campaign.projectId)
                같은 프로젝트의 템플릿만 선택할 수 있다.
                → IllegalArgumentException (400)

            UtmTemplate.isDeleted()
                → IllegalArgumentException (400)

            Campaign.selectTemplate(template)

PublicCampaignController.selectTemplate(request, campaignId, body)
    principal(request, projectId, CAMPAIGNS_WRITE)
    CampaignService.selectTemplateForApiKey(projectId, campaignId, templateId)
        findForApiKey로 캠페인을 찾은 뒤 같은 applyTemplateSelection을 탄다.
```

**핵심 1가지**

- **템플릿을 바꿔도 기존 링크의 UTM 값은 지워지지 않는다.** `link_utm_values`는 그대로 남고, 리다이렉트 시 `findEffectiveByLinkId`가 **새 템플릿의 활성 필드**만 조회하므로 새 템플릿에 없는 필드는 자연히 무시된다. 템플릿을 되돌리면 값이 다시 살아난다.

---

### UTM 기본값 조회

`GET /api/web/campaigns/{campaignId}/utm-defaults` · `GET /api/v1/campaigns/{campaignId}/utm-defaults`

```
CampaignController.defaults(principal, campaignId)

    CampaignService.defaults(userId, campaignId)
        @Transactional(readOnly = true)
        get(userId, campaignId)                  ← VIEWER 권한으로 충분

        activeDefaults(campaign)
            [템플릿이 없으면] 빈 목록.

            UtmTemplateFieldRepository.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(templateId)
                현재 활성 필드 이름 집합을 만든다.

            CampaignUtmDefaultRepository.findByCampaignIdOrderByFieldNameAsc(campaignId)
                저장된 기본값 전부를 읽고, 위 활성 집합에 있는 것만 남긴다.
                삭제된 필드의 기본값 행은 남아 있지만 응답에는 나오지 않는다.

    toDefaultsMap(defaults)
        List<CampaignUtmDefault> → Map<fieldName, defaultValue>
```

---

### UTM 기본값 수정

`PATCH /api/web/campaigns/{campaignId}/utm-defaults` · `PATCH /api/v1/campaigns/{campaignId}/utm-defaults`

**키의 유무와 값의 null이 서로 다른 의미**를 갖는다.

| 요청 | 의미 |
|---|---|
| 키가 없음 | 그 필드는 변경하지 않음 |
| `"utm_source": null` | 기본값 삭제 |
| `"utm_source": "google"` | upsert |

```
CampaignController.updateDefaults(principal, campaignId, request)

    CampaignService.updateDefaults(userId, campaignId, request.defaultsOrEmpty())
        @Transactional
        requireEditableCampaign(userId, campaignId)

        applyDefaultUpdates(campaign, updates)

            (템플릿 존재 확인)
                템플릿이 없으면 기본값을 둘 곳이 없다.
                → IllegalArgumentException (400)

            [updates의 각 항목마다]

                UtmTemplateFieldRepository.findByUtmTemplateIdAndNameAndDeletedAtIsNull(templateId, name)
                    활성 필드가 아니면 거부. 오타나 삭제된 필드에 기본값을 넣지 못하게.
                    → IllegalArgumentException (400)

                CampaignUtmDefaultRepository.findByCampaignIdAndFieldName(campaignId, name)

                [값이 null]     기존 행이 있으면 delete
                [기존 행 있음]  CampaignUtmDefault.updateValue(trimmed)
                [기존 행 없음]  CampaignUtmDefaultRepository.saveAndFlush(create(...))
                                → DataIntegrityViolationException → IllegalArgumentException (400)

                validDefaultValue(value)
                    500자 이하.
                    → IllegalArgumentException (400)

    CampaignService.defaults(...)  를 다시 호출해 갱신된 전체 맵을 응답한다.
```

**핵심 1가지**

- **항목 검증이 순차적이라 부분 적용이 가능하다.** 3개 중 2번째가 잘못된 필드명이면 예외가 나지만, 같은 `@Transactional` 안이므로 첫 번째 변경도 함께 롤백된다. 위의 캠페인 수정과 달리 여기는 원자적이다.

---

### 캠페인 링크 생성

`POST /api/web/campaigns/{campaignId}/links` · `POST /api/v1/campaigns/{campaignId}/links`

```
CampaignController.createLink(principal, campaignId, request)
    (Bean Validation) CreateCampaignLinkRequest 제약
    → 201 Created

    CampaignLinkCreationService.createForUser(userId, campaignId, request)
        @Transactional

        CampaignService.requireEditableCampaign(userId, campaignId)
        ProjectMemberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
            createdBy로 쓸 User를 얻는다. requireEditableCampaign이 멤버십을 반환하지 않아
            같은 조회를 한 번 더 한다.
            → SecurityException (403)

        create(campaign, project, createdBy, null, null, null, request)

PublicCampaignController.createLink(request, campaignId, idempotencyKey, body)
    principal(request, projectId, LINKS_WRITE)

    CampaignLinkCreationService.createForApiKey(keyId, projectId, campaignId, idempotencyKey, request)
        @Transactional

        RateLimitService.checkApiKeyWrite(apiKeyId)
            분당 60회.
            → RateLimitExceededException (429)

        CampaignService.findForApiKey(projectId, campaignId)

        validIdempotencyKey(idempotencyKey)
            [A-Za-z0-9._:-]{1,100}
            → IllegalArgumentException (400)

        requestFingerprint(request)
            originalUrl + expiresAt + externalId + name + (정렬된 UTM 맵) 을 SHA-256.
            UTM을 TreeMap으로 정렬해 키 순서가 달라도 같은 지문이 나오게 한다.

        create(campaign, project, null, apiKeyId, key, requestHash, request)

── 이후는 두 표면 공통 ──

    CampaignLinkCreationService.create(...)

        resolveUtmValues(template, request.utmValuesOrEmpty())

            [템플릿이 없는데 UTM 값을 보냈으면] → IllegalArgumentException (400)

            UtmTemplateFieldRepository.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(templateId)
                활성 필드 목록.

            (요청 키 검증)
                활성 필드에 없는 이름을 보내면 거부.
                → IllegalArgumentException (400)

            (값 수집)
                빈 문자열·공백은 저장하지 않는다 — 저장하지 않아야 리다이렉트 시
                캠페인 기본값으로 폴백되기 때문. "명시하지 않음"과 "빈 값"을 같게 취급한다.
                각 값은 500자 이하. → IllegalArgumentException (400)

        LinkManagementService.createForCampaign(url, expiresAt, project, createdBy,
                                                apiKeyId, key, hash, campaign, template, externalId, resolved, name)

            findIdempotentLink(apiKeyId, idempotencyKey, requestHash)
                기존 링크가 있고 지문이 같으면 그대로 반환.
                → 지문이 다르면 IdempotencyConflictException (409)

            (목적지 결정)
                originalUrl이 없으면 campaign.defaultOriginalUrl을 쓴다.
                둘 다 없으면 검증 자체를 건너뛴다 — 목적지 없는 링크를 만들 수 있다는 뜻이고,
                리다이렉트 시 410 NO_DESTINATION이 된다.

            UrlValidator.validate(DestinationUrlMerger.merge(destination, utm))
                UTM을 병합한 최종 URL로 검증한다. UTM 때문에 2048자를 넘기는 경우를 잡는다.
                (위험 검사는 생략 — 신뢰 정책)

            saveCampaignLinkWithUniqueCode(...)
                코드 충돌 시 최대 5회 재시도.
                → uq_links_campaign_external_id 위반이면 ExternalIdConflictException (409)
                → 그 외 충돌이면 멱등 조회를 한 번 더 시도

            LinkUtmValueRepository.save(LinkUtmValue.create(link, name, value))
                해석된 값만 링크별로 저장한다. 나머지는 리다이렉트 시 캠페인 기본값에서 온다.
```

**핵심 3가지**

- **링크에는 "명시한 UTM"만 저장한다.** 나머지는 리다이렉트 시점에 캠페인 기본값에서 채워지므로, 기본값을 바꾸면 기존 링크의 UTM도 함께 바뀐다.
- **`externalId`가 캠페인 내 유일하다.** DB unique 제약(`uq_links_campaign_external_id`)이 있어 같은 외부 ID로 두 번 만들 수 없다. 외부 시스템의 재시도를 막는 두 번째 장치다.
- **멱등성은 v1에만 있다.** web은 `Idempotency-Key`를 받지 않는다.

---

### 캠페인 링크 일괄 생성

`POST /api/v1/campaigns/{campaignId}/links/batch` — **v1 전용**, 최대 500개

```
PublicCampaignController.createBatch(request, campaignId, idempotencyKey, items)
    @RequestHeader("Idempotency-Key") — 필수다. 없으면 Spring이 먼저 막는다.
    principal(request, projectId, LINKS_WRITE)
    → 201 Created

    CampaignLinkBatchService.createBatch(apiKeyId, projectId, campaignId, idempotencyKey, items)
        @Transactional — 전체가 하나의 트랜잭션. 부분 성공이 없다.

        (사전 검증)
            Idempotency-Key 필수 / 항목 1개 이상 / 500개 이하
            → IllegalArgumentException (400)

        CampaignService.findForApiKey(projectId, campaignId)

        fingerprint(items)
            각 항목의 requestFingerprint를 "|"로 이어 붙인 뒤 다시 SHA-256.
            항목 순서까지 지문에 반영된다.

        CampaignLinkBatchRepository.findByApiKeyIdAndIdempotencyKey(apiKeyId, idempotencyKey)
            [이미 있고 지문이 같으면] linksForBatch(batchId)로 기존 결과를 그대로 반환.
                CampaignLinkBatchItemRepository.findByBatchIdOrderByIdItemIndexAsc(batchId)
                LinkRepository.findAllById(linkIds)
                    findAllById는 순서를 보장하지 않으므로 itemIndex 순서대로 다시 정렬한다.
            [지문이 다르면] → BatchIdempotencyConflictException (409)

        RateLimitService.checkJsonBatch(projectId)
            분당 2회. → RateLimitExceededException (429)

        RateLimitService.checkBulkLinkQuota(projectId, items.size())
            일 50,000 링크. 항목 수만큼 한 번에 소비한다.
            → RateLimitExceededException (429)

        [항목마다] CampaignLinkCreationService.createWithinBatch(campaign, item)
            링크 단위 멱등성 없이 생성한다. 멱등성은 batch 전체 단위로만 다룬다.

        CampaignLinkBatchRepository.saveAndFlush(CampaignLinkBatch.create(...))
            링크를 다 만든 뒤에야 batch 행을 쓴다.
            → 동시에 같은 키로 들어온 다른 요청이 먼저 커밋했으면
              DataIntegrityViolationException → BatchIdempotencyConflictException (409)
              이 트랜잭션은 통째로 롤백되므로 링크도 남지 않는다. 재시도하면 기존 결과를 받는다.

        [항목마다] CampaignLinkBatchItemRepository.save(CampaignLinkBatchItem.create(batch, index, linkId))
            순서를 보존해 재시도 시 같은 순서로 돌려주기 위한 기록.
```

**핵심 2가지**

- **전부 성공하거나 전부 실패한다.** 499번째에서 실패해도 앞의 498개가 롤백된다. CSV 가져오기가 행 단위로 성공/실패를 나누는 것과 정반대 설계다.
- **batch 행을 마지막에 쓴다.** 그래서 동시 요청의 패자는 링크까지 롤백되고, 승자의 결과만 남는다. batch 행을 먼저 썼다면 링크가 중복 생성될 여지가 있다.

---

### 캠페인 링크 목록

`GET /api/web/campaigns/{campaignId}/links` · `GET /api/v1/campaigns/{campaignId}/links`

**두 표면의 응답이 다르다.** web은 유효 UTM 값까지 함께 내려준다.

```
CampaignController.links(principal, campaignId, cursor, limit)

    CampaignService.get(principal.userId(), campaignId)
        권한 검사만을 위한 호출. 반환값은 버린다.

    boundedLimit(limit)                          1~100

    LinkRepository.findByCampaignIdAndDeletedFalseOrderByIdDesc(campaignId, PageRequest.of(0, limit+1))
    LinkRepository.findByCampaignIdAndDeletedFalseAndIdLessThanOrderByIdDesc(campaignId, cursor, ...)
        커서 페이징.

    LinkUtmValueRepository.findEffectiveByLinkIds(linkIds)
        보이는 링크들의 유효 UTM을 IN 절로 한 번에 조회한다. 링크마다 조회하면 N+1이 된다.
        source 컬럼으로 값의 출처를 함께 준다 — 'LINK'(링크에 직접 지정) 또는 'CAMPAIGN_DEFAULT'.

    WebCampaignLinkPageResponse.of(page, limit, effectiveUtm)
        linkId로 그룹핑해 각 링크에 UTM 목록을 붙인다.

PublicCampaignController.links(request, campaignId, cursor, limit)
    principal(request, projectId, LINKS_READ)
    CampaignService.findForApiKey(projectId, campaignId)
    (같은 커서 페이징)

    CampaignLinkPageResponse.of(page, boundedLimit)
        UTM 조회를 하지 않는다. code·name·originalUrl·externalId·createdAt만.
```

**핵심 1가지**

- **web만 UTM을 붙이는 이유는 화면 요구사항이다.** 캠페인 링크 표에서 "이 값이 링크 고유값인지 캠페인 기본값인지"를 구분해 보여줘야 해서 `source`까지 필요하다. v1 소비자는 그 구분이 필요 없다고 보고 쿼리 한 번을 아꼈다.

---

### 캠페인 링크 일괄 삭제

`DELETE /api/web/campaigns/{campaignId}/links` — **web 전용**

```
CampaignController.deleteLinks(principal, campaignId, request)
    (Bean Validation) codes: @NotNull @Size(min=1, max=100),
                      각 원소 @NotBlank @Pattern("[0-9A-Za-z]{6}")
    → 200 + { deletedCount }

    CampaignService.deleteLinks(userId, campaignId, codes)
        @Transactional
        requireEditableCampaign(userId, campaignId)

        (중복 제거)
            LinkedHashSet으로 중복을 걷어낸다. 아래 개수 대조를 정확히 하기 위해.

        LinkRepository.softDeleteByCampaignIdAndCodeIn(campaignId, uniqueCodes)
            @Modifying(clearAutomatically = true, flushAutomatically = true)
            벌크 UPDATE라 영속성 컨텍스트를 우회한다. 그래서 전후로 flush·clear가 필요하다.
            campaignId 조건이 함께 걸려 있어 다른 캠페인의 링크는 지워지지 않는다.

        (개수 대조)
            삭제된 수가 요청한 수와 다르면 전체를 되돌린다.
            없는 코드·이미 삭제된 코드·다른 캠페인의 코드가 섞였다는 뜻.
            → IllegalArgumentException (400) — 트랜잭션 롤백
```

**핵심 1가지**

- **개수 대조가 곧 존재 검증이다.** 코드를 하나씩 조회해 확인하는 대신 벌크 UPDATE의 반환값을 세는 방식이라 쿼리가 한 번으로 끝난다. 대신 "어떤 코드가 문제였는지"는 알려주지 못한다.
