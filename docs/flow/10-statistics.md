# statistics — 통계

`link_access_events` 테이블 하나를 스코프만 바꿔가며 집계한다. 7개 엔드포인트가 전부 같은 서비스 메소드(`report`)로 수렴한다.

| Method | Path | 핸들러 | 스코프 | 인증 |
|---|---|---|---|---|
| GET | `/api/links/{code}/statistics` | `StatisticsController.anonymous` | LINK | secret key |
| GET | `/api/web/projects/{projectId}/statistics` | `project` | PROJECT | JWT (멤버) |
| GET | `/api/web/campaigns/{campaignId}/statistics` | `campaign` | CAMPAIGN | JWT (멤버) |
| GET | `/api/web/projects/{projectId}/links/{code}/statistics` | `projectLink` | LINK | JWT (멤버) |
| GET | `/api/v1/projects/{projectId}/statistics` | `publicProject` | PROJECT | `stats:read` |
| GET | `/api/v1/campaigns/{campaignId}/statistics` | `publicCampaign` | CAMPAIGN | `stats:read` |
| GET | `/api/v1/projects/{projectId}/links/{code}/statistics` | `publicLink` | LINK | `stats:read` |

**세 표면이 한 컨트롤러 안에 있다.** 다른 도메인은 web/v1을 별도 컨트롤러로 나눴는데 여기만 합쳐져 있다.

## 공통 쿼리 파라미터

| 이름 | 기본값 | 제약 |
|---|---|---|
| `from` | `to - 29일` | `from ≤ to` |
| `to` | 오늘 (Asia/Seoul) | 기간 최대 10년(3660일) |
| `bucket` | `DAY` | `DAY` 최대 366포인트 / `MONTH` 120 / `YEAR` 10 |
| `offset` | 0 | 0~100,000 |
| `limit` | 50 | 1~100 |

**시간대가 `Asia/Seoul`로 하드코딩돼 있다** (`StatisticsService.ZONE`). 날짜 경계와 `date_trunc`가 모두 이 기준이다.

## 권한 검사 — 컨트롤러가 직접

이 도메인은 `ProjectService.requireRole`도 `CampaignService.requireEditableCampaign`도 쓰지 않고 컨트롤러가 자체 헬퍼를 갖는다.

```
StatisticsController.member(userId, projectId)
    ProjectMemberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
        → SecurityException (403 PROJECT_ACCESS_DENIED)
    (프로젝트 삭제 검사)
        → SecurityException (403)
    역할은 보지 않는다 — 멤버이기만 하면 VIEWER도 통계를 볼 수 있다.

StatisticsController.apiKey(request, projectId)
    request.getAttribute("srrrg.apiKeyPrincipal")
        → SecurityException (403) — 401이 아니다
    (projectId가 주어졌으면 일치 확인)
        → SecurityException (403)
    (STATS_READ 스코프 확인)
        → SecurityException (403)
```

`apiKey`가 `PublicApiException` 대신 `SecurityException`을 던지므로 **v1 통계 응답만 `ProblemDetail`이 아니라 `ApiErrorResponse` 형식**이다. `PublicCampaignApiExceptionHandler`의 적용 대상(`assignableTypes`)에 `StatisticsController`가 없어 `GlobalExceptionHandler`로 떨어지기 때문이다.

---

### 스코프별 진입 흐름

```
StatisticsController.anonymous(code, secret, from, to, bucket, offset, limit)
    익명 링크 통계. secret key가 인증 수단.

    LinkManagementService.getManagedLink(code, secret)
        인증만을 위한 호출. 반환값은 버린다.
        → LinkNotFoundException (404) / LinkGoneException (410)

    LinkRepository.findByCodeAndProjectIsNull(code)
        같은 링크를 다시 조회한다 — getManagedLink가 엔티티가 아닌 DTO를 반환하기 때문.
        → LinkNotFoundException (404)

    StatisticsService.link(linkId, code, ...)


StatisticsController.project(principal, projectId, ...)
    member(userId, projectId)
    StatisticsService.project(projectId, member.getProject().getName(), ...)


StatisticsController.campaign(principal, campaignId, ...)
    campaign(campaignId)
        CampaignRepository.findById → CampaignNotFoundException (404)
    member(userId, campaign.getProject().getId())
        캠페인을 먼저 찾고 그 프로젝트로 권한을 확인한다.
    StatisticsService.campaign(campaignId, campaign.getName(), ...)


StatisticsController.projectLink(principal, projectId, code, ...)
    member(userId, projectId)
    projectLink(projectId, code)
        LinkRepository.findByProjectIdAndCode(projectId, code)
        삭제된 링크는 410이 아니라 404로 처리한다 — 통계에서는 존재하지 않는 것과 같다.
        → LinkNotFoundException (404)
    StatisticsService.link(link.getId(), code, ...)


StatisticsController.publicProject(request, projectId, ...)
    apiKey(request, projectId)
    StatisticsService.project(projectId, "project-" + key.projectId(), ...)
        프로젝트 이름 대신 "project-{id}" 를 쓴다. 이름 조회를 생략한 것.


StatisticsController.publicCampaign(request, campaignId, ...)
    apiKey(request, null)
        projectId를 경로에서 받지 않으므로 여기서는 일치 검사를 건너뛴다.
    campaign(campaignId)
    (캠페인의 프로젝트와 키의 프로젝트 일치 확인)
        조회 후에 비교한다. 조회 자체는 막지 않으므로 캠페인 존재 여부는 404/403으로 구분된다.
        → SecurityException (403)


StatisticsController.publicLink(request, projectId, code, ...)
    apiKey(request, projectId)
    projectLink(projectId, code)
    StatisticsService.link(link.getId(), code, ...)
```

---

### 공통 집계 — StatisticsService.report

일곱 경로가 전부 여기로 모인다.

```
StatisticsService.report(scope, name, requestedFrom, requestedTo, bucket, offset, limit)
    @Transactional 없음 — 전부 읽기 쿼리다.

    validatePage(offset, limit)
        offset 0~100,000 / limit 1~100
        → IllegalArgumentException (400)

    period(from, to)
        to 기본값은 Asia/Seoul 기준 오늘, from 기본값은 to - 29일.
        start = from 00:00 KST, end = to+1 00:00 KST (반열린 구간).
        previousStart = start - (end - start) — 직전 동일 길이 구간. 증감 비교용.
        → from > to 또는 기간 10년 초과 시 IllegalArgumentException (400)

    validateBucketRange(period, bucket)
        DAY 366 / MONTH 120 / YEAR 10 포인트 상한.
        응답 배열이 무한정 커지는 것을 막는다.
        → IllegalArgumentException (400)

    StatisticsQueryRepository.totals(scope, period)
        현재 구간과 직전 구간의 합계를 한 쿼리로 낸다.
        COUNT(...) FILTER (WHERE ...) 를 12개 나열해 총계·리다이렉트·사람·봇을
        두 구간분 동시에 집계한다.
        links LEFT JOIN link_access_events — 이벤트가 없는 링크도 스코프에 포함시키기 위해.

    StatisticsQueryRepository.lifetimeLinkSummary(scope)
        기간과 무관한 링크 단위 요약. 서브쿼리로 링크별 이벤트 수를 센 뒤
        총 링크 수 / 사람 유입 있는 링크 / 봇만 온 링크 / 아무 유입 없는 링크 /
        단일 링크 / 캠페인 링크 6개를 낸다.

    (스코프별 목록)
        [CAMPAIGN] campaignLinks(id, period, offset, limit)  — 링크별 성과, 기간 유입 내림차순
                   campaignUtm(id, period, offset, limit)    — UTM 조합별 성과
        [PROJECT]  projectCampaigns(id, period, offset, limit) — 캠페인별 성과
        [LINK]     셋 다 빈 결과

    StatisticsQueryRepository.outcomes(scope, period)
        outcome(REDIRECTED / BLOCKED / CHECK_FAILED / URL_CHANGED / EXPIRED)별 집계.

    StatisticsQueryRepository.breakdown(scope, period, dimension)   × 4회
        REFERRER / DEVICE / BROWSER / OPERATING_SYSTEM 각각 상위 10개.

    StatisticsQueryRepository.recent(scope, period)
        최근 20건의 원본 이벤트. accessed_at 내림차순.

    completeTrend(queries.trend(scope, period, bucket), period, bucket)
        SQL의 date_trunc는 이벤트가 있는 날짜만 돌려준다.
        여기서 from~to를 순회하며 빈 날짜를 0으로 채워 넣는다.
        그래프에 구멍이 생기지 않게 하는 후처리.

    → StatisticsResponse(scope, name, from, to, bucket, summary, trend, outcomes,
                         referrers, devices, browsers, operatingSystems, recent,
                         links, utm, campaigns)
```

**핵심 3가지**

- **한 요청에 쿼리가 10개 안팎 나간다.** totals, lifetime, trend, outcomes, breakdown ×4, recent, 그리고 스코프별 목록 1~2개. 병렬화나 캐싱은 없다.
- **스코프는 SQL 술어 한 줄로 구현된다.** `StatisticsQueryScope.predicate()`가 `l.id = ?` / `l.campaign_id = ?` / `l.project_id = ?` 중 하나를 돌려주고, 모든 쿼리가 이 문자열을 `%s`로 끼워 넣는다. 세 스코프가 같은 쿼리 집합을 공유하는 방식.
- **삭제된 링크는 모든 집계에서 빠진다.** 술어에 `AND l.deleted_at IS NULL`이 붙어 있다. 이벤트 행은 남아 있지만 통계에는 절대 나타나지 않는다.

---

## UTM 통계가 특별한 이유

`campaignUtm`은 이 프로젝트에서 가장 복잡한 쿼리다. **"지금 설정된 UTM"과 "실제로 클릭될 때 붙었던 UTM"을 함께** 보여주려 하기 때문이다.

```
StatisticsQueryRepository.campaignUtm(campaignId, period, offset, limit)

    active_fields
        캠페인 템플릿의 활성 필드 이름.

    current_config
        지금 설정 기준 필드·값별 링크 수.
        COALESCE(link_utm_values.value, campaign_utm_defaults.default_value, '(없음)')
        — 리다이렉트가 쓰는 것과 같은 폴백 규칙.

    all_per_link / all_stats
        기간 무관, 실제 리다이렉트 이벤트에 스냅샷된 값 기준.
        event.effective_utm->>field.name — jsonb 컬럼에서 필드를 뽑는다.
        봇만 방문한 링크를 따로 센다.

    period_stats
        요청 기간의 리다이렉트 이벤트만.

    keys
        current_config와 all_stats의 (필드, 값) 조합을 UNION —
        "설정돼 있지만 아직 클릭 없음"과 "지금은 안 쓰지만 과거에 클릭됨" 양쪽을 모두 남긴다.
```

**핵심 1가지**

- **`effective_utm` jsonb 스냅샷이 이 쿼리를 가능하게 한다.** 클릭 시점의 UTM을 이벤트에 박아뒀기 때문에, 캠페인 기본값을 나중에 바꿔도 "그때 그 값으로 몇 번 클릭됐는지"를 되돌아볼 수 있다 ([06-link.md](06-link.md)의 접근 이벤트 저장 참고).
