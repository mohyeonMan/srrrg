# campaign — CSV 가져오기와 내보내기

CSV로 링크를 대량 생성하는 경로. **동기 요청은 파싱과 적재까지만 하고, 실제 링크 생성은 백그라운드 워커가 처리한다.** 이 프로젝트의 유일한 비동기 작업이다.

| 연산 | Path (`{base}` = `/api/web` 또는 `/api/v1`) | 필요 권한 |
|---|---|---|
| 템플릿 CSV 내려받기 | `GET {base}/campaigns/{campaignId}/links/template.csv` | VIEWER / `campaigns:read` |
| CSV 업로드 | `POST {base}/campaigns/{campaignId}/imports/csv` | EDITOR / `links:write` |
| 진행 상태 조회 | `GET {base}/campaigns/{campaignId}/imports/{importId}` | VIEWER / `campaigns:read` |
| 실패 행 CSV | `GET {base}/campaigns/{campaignId}/imports/{importId}/errors.csv` | VIEWER / `campaigns:read` |
| 링크 내보내기 | `GET {base}/campaigns/{campaignId}/links.csv` | VIEWER / `links:read` |

## CSV 형식

헤더는 `original_url`, `external_id`가 **필수**이고, 나머지 컬럼은 캠페인 템플릿의 **활성 UTM 필드 이름**이어야 한다.

```
original_url,external_id,utm_source,utm_medium
https://example.com/a,promo-001,google,cpc
```

모든 출력 CSV는 앞에 **BOM**을 붙인다 — Excel이 UTF-8로 인식하게 하기 위해서다.

## 제약과 DB 불변식

| 항목 | 값 | 지키는 방법 |
|---|---|---|
| 파일 크기 | 10MB | 애플리케이션 검사 |
| 데이터 행 | 10,000행 | 애플리케이션 검사 |
| 내보내기 행 | 10,000행 | `MAX_EXPORT_ROWS + 1` 조회 후 초과 판정 |
| 업로드 빈도 | 프로젝트당 시간당 5회 | `RateLimitService.checkCsvUpload` |
| 대량 링크 쿼터 | 프로젝트당 일 50,000개 | `RateLimitService.checkBulkLinkQuota` |
| 멱등성 | 캠페인당 `Idempotency-Key` 유일 | `uq_campaign_imports_campaign_idempotency` |
| **동시 import** | **프로젝트당 1개** | `uq_campaign_imports_active_project ... WHERE status IN ('PENDING','PROCESSING')` |
| 워커 lease | 5분, 최대 3회 시도 | `campaign_imports.lease_owner` / `lease_expires_at` |

---

### GET {base}/campaigns/{campaignId}/links/template.csv

빈 템플릿 CSV. 헤더만 있고 예시 행은 없다.

```
CampaignController.templateCsv(principal, campaignId)
    CampaignService.get(principal.userId(), campaignId)          ← VIEWER 권한 확인

PublicCampaignController.templateCsv(request, campaignId)
    principal(request, projectId, CAMPAIGNS_READ)
    CampaignService.findForApiKey(projectId, campaignId)

── 공통 ──

    CampaignCsvService.templateCsv(campaign)

        activeFieldNames(campaign)
            템플릿이 없으면 빈 목록 — original_url, external_id 두 컬럼만 나온다.
            UtmTemplateFieldRepository.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(templateId)

        (CSVPrinter로 헤더만 출력)
            본문 루프가 비어 있다. 예시 데이터를 넣으면 그대로 업로드하는 사용자가 생기므로 의도적으로 뺐다.

    csvAttachment(content, "campaign-{id}-template.csv")
        Content-Type: text/csv, Content-Disposition: attachment
```

---

### POST {base}/campaigns/{campaignId}/imports/csv

업로드. **동기 구간에서 파싱까지만** 하고 즉시 `202 Accepted`를 반환한다.

```
CampaignController.uploadCsv(principal, campaignId, idempotencyKey, file)
    @RequestHeader("Idempotency-Key") — 필수. 없으면 Spring이 먼저 막는다.
    → 202 Accepted

    CampaignService.requireEditableCampaign(userId, campaignId)     ← EDITOR
    ProjectMemberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
        createdBy로 기록할 User를 얻는다.
        → SecurityException (403)

    MultipartFile.getBytes()
        파일 전체를 메모리로 읽는다. 10MB 상한이 곧 메모리 상한.

    CampaignCsvService.startImport(campaign, bytes, key, uploader, createdByApiKeyId = null)

PublicCampaignController.uploadCsv(request, campaignId, idempotencyKey, file)
    principal(request, projectId, LINKS_WRITE)
    CampaignService.findForApiKey(projectId, campaignId)
    CampaignCsvService.startImport(campaign, bytes, key, createdBy = null, principal.keyId())

── 공통 ──

    CampaignCsvService.startImport(campaign, content, idempotencyKey, createdBy, createdByApiKeyId)
        @Transactional

        (사전 검사)
            Idempotency-Key 필수 / 빈 파일 아님 / 10MB 이하
            → IllegalArgumentException (400)

        decodeUtf8(content)
            앞 3바이트가 EF BB BF면 BOM을 떼어낸다.
            REPORT 모드로 디코딩해 UTF-8이 아니면 즉시 실패시킨다 — 깨진 문자를
            물음표로 흘려보내지 않기 위해.
            → IllegalArgumentException("UTF-8 또는 UTF-8 BOM 인코딩만 지원합니다.") (400)

        sha256(content)
            원본 바이트의 해시. 멱등성 판정 기준값.

        CampaignImportRepository.findByCampaignIdAndIdempotencyKey(campaignId, key)
            [있고 해시가 같으면] 기존 import를 그대로 반환한다. 재업로드가 아니라 재시도로 본다.
            [해시가 다르면] → CampaignImportIdempotencyConflictException (409 IDEMPOTENCY_CONFLICT)

        RateLimitService.checkCsvUpload(projectId)
            시간당 5회. → RateLimitExceededException (429)

        parse(text, campaign)

            activeFieldNames(campaign)
                허용 컬럼 집합을 만든다.

            CSVFormat.DEFAULT ... setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true)
                → 파싱 실패 시 IllegalArgumentException("CSV 형식을 읽을 수 없습니다.") (400)

            (헤더 검증)
                중복 컬럼 없음 / original_url·external_id 존재 /
                나머지 컬럼은 전부 활성 UTM 필드
                → IllegalArgumentException (400)

            (행 수 검증)
                0행이면 거부, 10,000행 초과면 거부.
                → IllegalArgumentException (400)

            [행마다] parseRow(record, campaign, utmHeaderNames, seenExternalIds)

                (길이 검사 → 선(先)실패 표시)
                    original_url 2048자 초과 → preFailureCode = "URL_TOO_LONG"
                    external_id 100자 초과   → preFailureCode = "EXTERNAL_ID_TOO_LONG"
                    값은 잘라서 저장한다 — 실패 CSV에 원본 흔적을 남기기 위해.
                    이 행들은 예외를 던지지 않고 "실패 예정"으로 표시만 한다.

                (파일 내 external_id 중복)
                    이것만은 업로드 전체를 거부한다. 중복이면 어떤 행을 살릴지 결정할 수 없으므로.
                    → IllegalArgumentException (400)

                CampaignLinkCreationService.resolveUtmValues(template, rawValues)
                    빈 셀은 값 없음으로 취급해 캠페인 기본값으로 폴백시킨다.
                    → IllegalArgumentException 발생 시 preFailureCode = "INVALID_UTM_VALUE" 로 흡수

        RateLimitService.checkBulkLinkQuota(projectId, rows.size())
            파싱이 끝나야 행 수를 알 수 있으므로 여기서 쿼터를 소비한다.
            → RateLimitExceededException (429)

        CampaignImportRepository.saveAndFlush(CampaignImport.create(...))
            → uq_campaign_imports_active_project 위반이면 ActiveImportConflictException (409 IMPORT_IN_PROGRESS)
              프로젝트당 진행 중 import는 하나뿐이다.
            → uq_campaign_imports_campaign_idempotency 위반이면 CampaignImportIdempotencyConflictException (409)
            → 그 외 제약 위반은 그대로 전파

        [행마다]
            CampaignImportRowRepository.save(CampaignImportRow.create(import, rowNumber, url, externalId))

            [선실패 행이면] row.fail(code, message) + campaignImport.recordRowResult(false)
                워커가 집기도 전에 이미 실패로 확정된다.

            [정상 행이면] CampaignImportRowUtmValueRepository.save(...)
                해석된 UTM 값을 행별로 저장해 둔다. 워커가 이 값을 읽어 링크를 만든다.

    → ImportResponse(id, status, totalRows, processedRows, succeededRows, failedRows, ...)
```

**핵심 3가지**

- **행 오류와 파일 오류를 구분한다.** 길이 초과·잘못된 UTM 값은 그 행만 실패로 표시하고 나머지는 처리한다. 반면 헤더 이상·인코딩 오류·파일 내 external_id 중복은 업로드 전체를 거부한다.
- **프로젝트당 진행 중 import가 하나뿐이다.** 부분 unique 인덱스가 강제하므로 애플리케이션 검사 없이도 두 번째 업로드는 409가 된다.
- **동기 구간이 짧지 않다.** 파싱과 최대 10,000행 × (행 1건 + UTM n건) INSERT가 전부 이 요청 안에서 일어난다. "비동기"는 링크 생성 부분에만 해당한다.

---

## 백그라운드 워커 (엔드포인트 아님)

업로드가 남긴 `PENDING` import를 집어 실제 링크를 만든다.

```
CampaignImportWorker.tick()
    @Scheduled(fixedDelay = 2s, initialDelay = 5s)
    모든 파드에서 각자 돈다. 클래스 스스로 @Transactional을 갖지 않는다 —
    Processor의 각 @Transactional 메소드를 외부에서 하나씩 호출해야
    self-invocation으로 프록시를 건너뛰지 않기 때문.

    CampaignImportProcessor.claimNext(workerId)
        @Transactional

        CampaignImportRepository.findNextClaimable(now)
            네이티브 쿼리:
                where status = 'PENDING'
                   or (status = 'PROCESSING' and lease_expires_at < :now)
                order by id asc limit 1
                for update skip locked

            SKIP LOCKED가 파드 간 중복 claim을 막고,
            lease_expires_at 조건이 죽은 파드의 작업을 회수한다.

        CampaignImport.tryAcquireLease(workerId, now, now + 5분, maxAttempts = 3)
            lease 소유자와 만료 시각을 기록하고 상태를 PROCESSING으로 바꾼다.
            시도 횟수가 3회를 넘으면 획득에 실패한다 — 무한 재시도 방지.

        → 집을 것이 없으면 null, 있으면 importId

    drain(importId)                                  [importId를 얻었을 때만]

        [반복, 최대 10,000행]

            CampaignImportProcessor.fetchPendingRowIds(importId)
                @Transactional(readOnly = true)
                PENDING 행을 rowNumber 순으로 100개씩 가져온다.
                빈 목록이면 루프 종료.

            [행마다] CampaignImportProcessor.processRow(importId, rowId)
                @Transactional — 행 하나가 트랜잭션 하나다.
                한 행이 실패해도 다른 행에 영향을 주지 않는 이유.

                (상태 재확인)
                    행이 이미 PENDING이 아니면 무시한다. 다른 파드가 처리했을 수 있다.

                (import 취소 확인)
                    import 상태가 PROCESSING이 아니면 IMPORT_CANCELLED로 실패시킨다.
                    캠페인 삭제(cancelActiveByCampaignId)가 여기서 반영된다.

                CampaignImportRowUtmValueRepository.findByImportRowId(rowId)
                    업로드 시 저장해 둔 UTM 값을 읽는다.

                LinkManagementService.createForCampaign(url, null, project, null, null, null, null,
                                                        campaign, template, externalId, resolved)
                    링크 생성은 web·v1·batch와 완전히 같은 경로를 탄다.

                CampaignImportRow.succeed(linkId) / CampaignImport.recordRowResult(true)

                (실패 분류)
                    ExternalIdConflictException  → "EXTERNAL_ID_CONFLICT"
                    UnsafeUrlException           → "URL_THREAT_DETECTED"
                    UrlRiskCheckFailedException  → "URL_CHECK_FAILED"
                    IllegalArgumentException     → "INVALID_ROW"
                    그 외 RuntimeException       → "UNEXPECTED_ERROR" (로그에 스택 기록)
                    어떤 경우든 예외를 밖으로 던지지 않는다. 한 행이 워커를 멈추면 안 되므로.

        CampaignImportProcessor.finalizeImport(importId)
            @Transactional

            (상태 확인) PROCESSING이 아니면 아무것도 하지 않는다.

            (남은 PENDING 행 확인)
                하나라도 남아 있으면 완료 처리하지 않는다.
                다른 파드가 아직 처리 중일 수 있다.

            CampaignImport.complete(now)
                completedAt을 찍고 상태를 종료로 바꾼다.
                → uq_campaign_imports_active_project의 자리를 비워 다음 업로드가 가능해진다.
```

**핵심 3가지**

- **`SKIP LOCKED` + lease가 멀티 파드 설계의 핵심이다.** 전자는 동시 claim을, 후자는 파드 사망 후 복구를 담당한다. 단일 인스턴스였다면 둘 다 불필요하다.
- **행 하나 = 트랜잭션 하나.** 10,000행짜리 import는 트랜잭션을 10,000번 연다. 느리지만 어떤 행이 실패해도 나머지가 살아남는다.
- **워커가 자기 클래스의 메소드를 호출하지 않는다.** `CampaignImportWorker`가 `CampaignImportProcessor`의 메소드를 하나씩 부르는 구조인데, 이는 Spring의 `@Transactional`이 프록시 기반이라 같은 빈 안에서 호출하면 트랜잭션이 걸리지 않기 때문이다. 두 클래스로 나눈 유일한 이유다.

---

### GET {base}/campaigns/{campaignId}/imports/{importId}

진행 상태 폴링용.

```
CampaignController.importStatus(principal, campaignId, importId)
    CampaignService.get(principal.userId(), campaignId)          ← VIEWER

    importOrNotFound(campaignId, importId)
        CampaignImportRepository.findByIdAndCampaignId(importId, campaignId)
            campaignId를 함께 걸어 다른 캠페인의 import를 볼 수 없게 한다.
            → web:  IllegalArgumentException (400)
            → v1:   PublicApiException 404 IMPORT_NOT_FOUND

PublicCampaignController.importStatus(request, campaignId, importId)
    principal(request, projectId, CAMPAIGNS_READ)
    CampaignService.findForApiKey(projectId, campaignId)
    importOrNotFound(campaignId, importId)

    → ImportResponse(id, status, totalRows, processedRows, succeededRows, failedRows, createdAt, completedAt)
```

**핵심 1가지**

- **같은 상황에 두 표면의 상태 코드가 다르다.** web은 400, v1은 404다. 두 컨트롤러가 각자 헬퍼를 갖고 있어 생긴 차이다.

---

### GET {base}/campaigns/{campaignId}/imports/{importId}/errors.csv

실패한 행만 CSV로. 고쳐서 다시 업로드하는 용도다.

```
CampaignController.importErrorsCsv(principal, campaignId, importId)
    CampaignService.get(...)  /  importOrNotFound(campaignId, importId)

    CampaignCsvService.errorCsv(campaignImport)

        CampaignImportRowRepository.findByCampaignImportIdAndStatusOrderByRowNumberAsc(importId, FAILED)
            페이징이 없다. 10,000행 전부가 실패하면 전량을 메모리에 올린다.

        UtmTemplateFieldRepository.findByUtmTemplateIdOrderByNameAsc(templateId)
            deletedAt 필터가 없다 — 삭제된 필드까지 컬럼으로 넣는다.
            import 당시의 데이터를 온전히 되돌려주기 위한 선택.

        (헤더 구성)
            row_number, original_url, external_id, {UTM 필드들}, error_code, error_message

        [실패 행마다]
            CampaignImportRowUtmValueRepository.findByImportRowId(row.getId())
                행마다 조회한다 — N+1.

    csvAttachment(content, "import-{id}-errors.csv")
```

**핵심 1가지**

- **출력 헤더가 업로드 형식과 호환된다.** `row_number`, `error_code`, `error_message` 세 컬럼만 지우면 그대로 재업로드할 수 있다. 다만 `startImport`의 헤더 검증이 활성 UTM 필드가 아닌 컬럼을 거부하므로, 삭제된 필드 컬럼이 섞여 있으면 지워야 한다.

---

### GET {base}/campaigns/{campaignId}/links.csv

캠페인 링크 내보내기. 생성일 범위와 external_id 부분 일치로 거를 수 있다.

```
CampaignController.exportLinksCsv(principal, campaignId, createdFrom, createdTo, externalId)
    CampaignService.get(principal.userId(), campaignId)          ← VIEWER

PublicCampaignController.exportLinksCsv(request, campaignId, ...)
    principal(request, projectId, LINKS_READ)                    ← campaigns:read가 아니라 links:read
    CampaignService.findForApiKey(projectId, campaignId)

── 공통 ──

    CampaignCsvService.exportLinksCsv(campaign, createdFrom, createdTo, externalIdQuery)

        exportSpecification(campaignId, createdFrom, createdTo, "%query%")
            JPA Criteria Specification으로 동적 조건을 조립한다.
            campaign 일치 + deleted = false 는 항상 포함.
            externalId는 LIKE '%...%' 부분 일치 — 앞 와일드카드라 인덱스를 타지 못한다.

        LinkRepository.findAll(spec, PageRequest.of(0, MAX_EXPORT_ROWS + 1, Sort.DESC "id"))
            10,001개를 읽어 초과 여부를 판정한다.
            → 초과하면 IllegalArgumentException("조회 조건을 좁혀주세요.") (400)

        activeFieldNames(campaign)
            여기서는 활성 필드만 컬럼으로 쓴다. errorCsv와 반대다.

        [링크마다] LinkUtmValueRepository.findByLinkId(link.getId())
            링크에 직접 지정된 값만 읽는다 — 캠페인 기본값은 포함되지 않는다.
            findEffectiveByLinkId가 아니라 findByLinkId라는 점에 주의.
            N+1이다.

        (헤더) code, short_url, original_url, external_id, {활성 UTM 필드}, created_at
            short_url은 baseUrl + "/" + code — 서브도메인을 반영하지 않는다.
```

**핵심 2가지**

- **내보낸 UTM 값은 실제 리다이렉트 결과와 다를 수 있다.** `findByLinkId`는 링크에 직접 저장된 값만 주므로, 캠페인 기본값에서 채워지는 파라미터는 빈 칸으로 나온다. 리다이렉트가 실제로 붙이는 값을 보려면 web 링크 목록 API의 `effectiveUtmValues`를 봐야 한다.
- **`short_url`이 서브도메인을 반영하지 않는다.** `LinkManagementService.projectShortUrl`은 `link.subdomain`을 보지만 이쪽은 `baseUrl`만 쓴다. 서브도메인 링크를 내보내면 실제로 배포된 URL과 다른 값이 나온다.
