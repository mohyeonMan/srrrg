# campaign — CSV 대량 생성과 내보내기

CSV로 캠페인 링크를 한 번에 생성하는 경로다. 업로드 요청 안에서 파일 전체를 검증하고,
문제가 없을 때만 모든 링크를 하나의 트랜잭션으로 생성한다.

| 연산 | Path (`{base}` = `/api/web` 또는 `/api/v1`) | 필요 권한 |
|---|---|---|
| 템플릿 CSV | `GET {base}/campaigns/{campaignId}/links/template.csv` | VIEWER / `campaigns:read` |
| CSV 대량 생성 | `POST {base}/campaigns/{campaignId}/imports/csv` | EDITOR / `links:write` |
| 링크 내보내기 | `GET {base}/campaigns/{campaignId}/links.csv` | VIEWER / `links:read` |

## CSV 형식과 제한

헤더는 `original_url`, `external_id`가 필수이고, 나머지는 캠페인 템플릿의 활성 UTM 필드여야 한다.

```csv
original_url,external_id,utm_source,utm_medium
https://example.com/a,promo-001,google,cpc
```

- UTF-8 또는 UTF-8 BOM만 허용한다.
- 파일은 최대 10MB, 데이터는 최대 10,000행이다.
- `external_id`는 캠페인 안과 파일 안에서 중복될 수 없다.
- 원본 URL이 비어 있으면 캠페인 기본 목적지가 유효해야 한다.
- UTM 값은 활성 필드와 500자 저장 제한만 검사하고, 병합 결과 URL을 다시 검증하지 않는다.
- 파일 전체를 먼저 검증하므로 한 행이라도 잘못되면 링크를 만들지 않는다.

## GET {base}/campaigns/{campaignId}/links/template.csv

`CampaignCsvController`와 `PublicCampaignCsvController`가 각 인증 표면에서 캠페인 접근을 확인한 뒤
`CampaignCsvService.templateCsv`를 호출한다. 출력은 필수 두 컬럼과 활성 UTM 필드 이름을 가진 헤더뿐이며,
Excel이 UTF-8로 인식하도록 BOM을 붙인다.

## POST {base}/campaigns/{campaignId}/imports/csv

```text
Controller
    웹: EDITOR 권한과 업로더 User 확인
    v1: links:write scope와 API key 확인

CampaignCsvService.createLinks(campaign, bytes, creator)
    파일 크기와 UTF-8 검사
    프로젝트별 CSV 업로드 빈도 검사
    전체 CSV 파싱
        헤더·행 수 검사
        행 번호별 URL·external_id·UTM 검사
        파일 내부 external_id 중복 검사
        기존 캠페인 external_id 일괄 조회
    프로젝트 일일 대량 링크 쿼터 검사
    [한 트랜잭션]
        각 행을 LinkCreationService.createForCampaign으로 생성
        하나라도 실패하면 전체 롤백

→ 200 OK { totalRows, createdRows }
```

파일 구조나 행 값이 잘못되면 `400 INVALID_REQUEST`로 첫 문제의 데이터 행 번호, 컬럼과 이유를 반환한다.
저장 시점에 동시 요청과 `external_id`가 충돌하면 기존 링크 생성 계약대로 `409 EXTERNAL_ID_CONFLICT`를 반환하며,
그 전에 만든 행도 같은 트랜잭션에서 롤백된다.

CSV 업로드에는 `Idempotency-Key`를 사용하지 않는다. 같은 파일을 다시 보내면 새로운 생성 요청으로 처리된다.
별도 작업 상태, lease, 진행률 조회와 실패 CSV도 없다.

## GET {base}/campaigns/{campaignId}/links.csv

캠페인 링크를 생성일 범위와 `external_id` 부분 일치 조건으로 내보낸다. 최대 10,000개이며 초과하면
조건을 좁히라는 `400`을 반환한다. 헤더는 `code`, `short_url`, `original_url`, `external_id`,
활성 UTM 필드, `created_at` 순서이고 UTF-8 BOM을 붙인다.

현재 내보내기는 링크에 직접 저장된 UTM 값만 포함하므로 캠페인 기본값에서 상속되는 값은 빈 칸이다.
또한 `short_url`은 기본 `srrrg.base-url`을 사용해 링크의 서브도메인을 반영하지 않는다.
