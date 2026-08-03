# Secret key 기반 링크 관리 기능 구현 명세

## 1. 목표

메인 화면의 `단축 링크 만들기` 영역 아래에 링크 관리 영역을 추가한다.

사용자는 다음 두 값을 입력해 자신이 만든 링크를 조회한다.

- 단축 URL 또는 6자리 단축 코드
- 링크 생성 시 한 번 발급된 secret key

조회에 성공하면 링크 정보와 누적 통계를 확인하고 다음 관리 작업을 수행할 수 있다.

- 원본 URL 확인 및 변경
- 만료 시각 확인, 변경 및 제거
- 링크 soft delete

인증 방식은 `code + secret key` 조합을 사용한다. 단축 코드는 공개 식별자이고 secret key는 관리 권한을 증명하는 비밀값으로 취급한다.

## 2. 구현 상태

- `GET /api/links/{code}`로 링크 정보와 누적 통계를 조회한다.
- `PATCH /api/links/{code}`로 원본 URL과 만료 시각을 부분 수정한다.
- `DELETE /api/links/{code}`로 링크를 soft delete한다.
- 모든 관리 API는 code 조회 후 `SecretKeyManager.matches`로 SHA-256 해시를 상수 시간 비교한다.
- 메인 화면에 관리 조회, 수정, 삭제 UI가 연결되어 있다.
- 서비스 정책 단위 테스트와 MockMvc HTTP 계약 테스트가 있다.

## 3. 이번 범위

### 포함

- 메인 화면의 링크 관리 조회 폼
- code와 secret key를 이용한 링크 상세 조회
- 누적 진입 수와 실제 이동 수 표시
- 원본 URL 부분 수정
- 만료 시각 변경 및 제거
- soft delete
- 관리 API용 JSON 오류 응답
- OpenAPI 명세
- 서비스 정책 및 HTTP 계약 테스트

### 제외

- secret key 재발급 및 분실 복구
- 일별 차트와 기간별 통계 조회
- 브라우저, OS, device, referer별 상세 통계
- 회원가입 및 로그인
- 여러 링크를 한 번에 보여주는 관리 목록
- rate limit 구현
- 물리 삭제

결과별 접근 이벤트는 저장하지만 이번 화면에서는 누적 `accessCount`, `redirectCount`만 보여준다. 기간별 통계는 실제 화면 요구가 생길 때 별도 API로 추가한다.

## 4. 화면 흐름

### 4.1 조회 입력

`단축 링크 만들기` 영역 아래에 별도의 관리 영역을 둔다.

```text
내 단축 링크 관리

[ 단축 URL 또는 코드                      ]
[ Secret key                              ]
[ 조회하기 ]
```

첫 번째 입력에는 다음 두 형식을 모두 허용한다.

```text
aB3x9Q
https://srrrg.link/aB3x9Q
```

전체 URL이 입력되면 프론트엔드에서 마지막 path segment를 code로 추출한다. 추출한 code는 6자리 Base62 형식인지 확인한 뒤 API를 호출한다.

### 4.2 조회 결과

조회 성공 시 같은 영역에 다음 정보를 표시한다.

```text
단축 URL
원본 URL
만료 여부 및 만료 시각
누적 진입 수
실제 이동 수
생성 시각
최근 수정 시각
```

조회 이후의 PATCH와 DELETE 요청에도 사용자가 입력한 code와 secret key를 사용한다. secret key는 화면에 다시 출력하거나 브라우저 저장소에 자동 저장하지 않는다.

## 5. 인증 정책

관리 API는 모두 다음 헤더를 사용한다.

```http
X-Srrrg-Secret-Key: srrrg_sk_xxxxxxxxx
```

query string과 JSON body에는 secret key를 넣지 않는다. URL, 브라우저 방문 기록, 프록시 접근 로그에 secret key가 남을 가능성을 줄이기 위해서다.

인증 순서는 다음과 같다.

```text
1. 요청 헤더 존재 여부 확인
2. code로 링크 조회
3. 입력한 secret key의 SHA-256 해시와 secret_key_hash를 상수 시간 비교
4. 인증 성공 후 링크 상태 확인
5. 조회 또는 변경 수행
```

링크 존재 여부 노출을 줄이기 위해 다음 경우는 같은 응답을 사용한다.

```text
존재하지 않는 code
잘못된 형식의 secret key
일치하지 않는 secret key
→ 404 LINK_NOT_FOUND
```

헤더 자체가 누락된 경우는 링크 존재 여부와 무관한 요청 형식 오류이므로 `400 INVALID_REQUEST`를 반환한다.

삭제된 링크는 반드시 secret key 검증을 먼저 수행한다. 올바른 key를 제출한 경우에만 `410 LINK_GONE`을 반환하고, 잘못된 key에는 404를 반환한다.

## 6. 링크 사용 가능 여부 정책

- 삭제된 링크는 관리 API와 리다이렉트에서 `410 Gone`을 반환한다.
- 만료된 링크는 리다이렉트에서 `410 Gone`을 반환한다.
- 만료된 링크도 올바른 secret key로 관리 정보를 조회할 수 있다.
- 만료된 링크는 만료 시각을 미래로 연장하거나 제거해 다시 사용할 수 있다.

## 7. API 계약

## 7.1 링크 관리 정보 조회

```http
GET /api/links/{code}
X-Srrrg-Secret-Key: srrrg_sk_xxxxxxxxx
Accept: application/json
```

성공 응답은 `200 OK`를 사용한다.

```json
{
  "code": "aB3x9Q",
  "shortUrl": "https://srrrg.link/aB3x9Q",
  "originalUrl": "https://example.com/very/long/url",
  "expiresAt": "2026-12-31T14:59:59Z",
  "statistics": {
    "accessCount": 120,
    "redirectCount": 93
  },
  "createdAt": "2026-07-10T10:00:00Z",
  "updatedAt": "2026-07-10T10:00:00Z"
}
```

`accessCount`는 사용 가능한 단축 URL의 진입 횟수이고 `redirectCount`는 위험 검사를 통과해 원본 URL 응답을 발행한 횟수다. 차단, 검사 실패 또는 검사 중 URL 변경이 발생하면 접근만 기록하므로 두 값은 같지 않을 수 있다.

## 7.2 링크 정보 수정

```http
PATCH /api/links/{code}
X-Srrrg-Secret-Key: srrrg_sk_xxxxxxxxx
Content-Type: application/json
```

요청 가능한 필드는 다음 두 개다.

```json
{
  "originalUrl": "https://new-example.com",
  "expiresAt": "2027-01-31T14:59:59Z"
}
```

부분 수정 규칙:

- `originalUrl` 생략: 기존 원본 URL 유지
- `expiresAt` 생략: 기존 만료 시각 유지
- `expiresAt: null`: 만료 시각을 제거하고 무기한으로 변경
- `originalUrl: null`: 400
- 모든 필드 생략: 400
- 과거 또는 현재와 같은 만료 시각: 400
- 변경된 원본 URL은 생성 API와 같은 `UrlValidator` 정책으로 재검증

JSON 필드의 생략과 명시적인 `null`을 구분해야 하므로 `UpdateLinkRequest`는 단순 record 대신 각 필드의 전달 여부를 추적할 수 있는 DTO로 작성한다.

성공 응답은 `200 OK`와 갱신된 관리 정보 전체를 반환한다. 조회와 동일한 응답 DTO를 사용해 프론트엔드가 별도 재조회 없이 화면을 갱신할 수 있게 한다.

## 7.3 링크 삭제

```http
DELETE /api/links/{code}
X-Srrrg-Secret-Key: srrrg_sk_xxxxxxxxx
```

성공 응답은 `200 OK`를 사용한다.

```json
{
  "deleted": true
}
```

삭제는 `is_deleted = true`로 처리한다. 삭제 확인 UI를 거쳐 요청하며 반복 삭제 요청은 `410 LINK_GONE`을 반환한다.

## 8. 오류 응답

오류 응답은 기존 `ApiErrorResponse` 형식을 유지한다.

```json
{
  "code": "LINK_NOT_FOUND",
  "message": "링크를 찾을 수 없습니다."
}
```

| 상황 | HTTP 상태 | 오류 코드 |
|---|---:|---|
| secret key 헤더 누락 | 400 | `INVALID_REQUEST` |
| PATCH body 형식 오류 | 400 | `INVALID_REQUEST` |
| 변경 필드 없음 | 400 | `INVALID_REQUEST` |
| URL 또는 만료 정책 위반 | 400 | `INVALID_REQUEST` |
| code 없음 또는 secret key 불일치 | 404 | `LINK_NOT_FOUND` |
| 인증된 요청이 삭제된 링크에 접근 | 410 | `LINK_GONE` |

`RedirectExceptionHandler`는 기존처럼 리다이렉트 컨트롤러의 HTML 오류 화면을 담당한다. `GlobalExceptionHandler`에는 관리 API가 사용할 404와 410 JSON 응답을 추가한다.

## 9. 데이터베이스 변경

누적 통계는 `links` 테이블의 `access_count`, `redirect_count`를 사용한다. 상세 기록은 결과를 포함한 `link_access_events` 한 테이블에 저장한다.
접근 결과는 `REDIRECTED`, `BLOCKED`, `CHECK_FAILED`, `URL_CHANGED`로 구분하며, `REDIRECTED`인 경우에만 `redirect_count`도 함께 증가한다.

## 10. 파일 구성

```text
src/main/java/link/srrrg/link
├── Link.java
├── LinkService.java
├── LinkController.java
└── dto
    ├── LinkManagementResponse.java
    ├── LinkStatisticsSummary.java
    ├── UpdateLinkRequest.java
    └── DeleteLinkResponse.java
```

기존 클래스의 변경 방향:

### `Link`

- 원본 URL과 만료 시각을 변경하는 의도가 드러나는 메서드 추가
- soft delete 메서드 추가
- public setter는 추가하지 않음

### `LinkService`

- code 조회 및 secret key 검증 공통 로직
- 조회, 부분 수정, 삭제 유스케이스
- 관리 응답 변환
- 조회는 read-only transaction, 수정과 삭제는 transaction 적용

### `LinkController`

- 기존 `/api/links` 아래 GET, PATCH, DELETE 추가
- `X-Srrrg-Secret-Key` 헤더 수신
- 검증과 엔티티 변경 로직은 포함하지 않음

### `LinkRepository`

기존 `findByCode`로 인증할 링크를 조회한다.

## 11. 구현 순서

### 1단계: 엔티티 변경 기능

- 만료 변경, URL 변경, soft delete 메서드 추가

완료 기준: 외부 setter 없이 관리 API에 필요한 상태 변경을 수행할 수 있다.

### 2단계: 인증된 조회 API

- 관리 응답 DTO 작성
- code 조회 후 SHA-256 해시를 검증하는 공통 로직 작성
- `GET /api/links/{code}` 구현
- JSON 400, 404, 410 오류 처리 추가

완료 기준: 올바른 code와 secret key로만 링크 및 누적 통계를 조회할 수 있다.

### 3단계: 수정과 삭제 API

- PATCH 필드 전달 여부 처리
- URL과 만료 시각 검증
- DELETE soft delete 구현
- OpenAPI 요청과 응답 명세 추가

완료 기준: 전달된 필드만 수정되고 삭제된 링크는 관리와 리다이렉트 모두 차단된다.

### 4단계: 메인 화면 관리 UI

- 생성 영역 아래 관리 조회 폼 추가
- 단축 URL에서 code 추출
- 조회 결과와 통계 표시
- 만료 시각과 원본 URL 수정 UI 연결
- 삭제 확인 UI 연결
- loading, validation, API 오류 상태 표시

완료 기준: 페이지 새로고침 후에도 사용자가 code와 secret key를 직접 입력해 전체 관리 흐름을 수행할 수 있다.

## 12. 테스트 계획

### 서비스 단위 테스트

- 올바른 code와 secret key로 조회 성공
- 없는 code와 틀린 secret key가 같은 `LinkNotFoundException`을 발생
- 인증 실패 시 링크를 변경하지 않음
- 만료된 링크 조회와 만료 연장
- 만료 시각 제거
- URL 변경 시 정책 재검증
- 빈 PATCH 거부
- soft delete와 반복 삭제 거부

### HTTP 계약 테스트

- GET, PATCH, DELETE의 header, status, body 검증
- secret key 헤더 누락 시 400
- 잘못된 key와 없는 code가 모두 404
- 삭제된 링크에 올바른 key로 접근 시 410
- 응답에 `secretKeyHash`와 secret key 원문이 포함되지 않음
- PATCH에서 필드 생략과 `expiresAt: null`이 구분됨

### 회귀 테스트

- 기존 링크 생성 API가 계속 201을 반환
- 사용 가능한 링크의 기존 리다이렉트 흐름 유지
- 최초 조회부터 만료 또는 삭제된 링크는 접근 이벤트를 추가하지 않음
- 기존 redirect HTML 오류 화면 유지

### 수동 확인 시나리오

```text
1. 메인 화면에서 링크 생성
2. 응답의 shortUrl과 secret key 보관
3. 관리 영역에 두 값을 입력해 조회
4. 진입 수와 실제 이동 수 확인
5. 만료 시각과 원본 URL 수정
6. 링크 삭제 후 관리 API와 단축 URL이 410인지 확인
```

## 13. 보안 및 운영 주의사항

- secret key 원문과 전체 요청 헤더를 로그에 남기지 않는다.
- 프론트엔드는 secret key를 `localStorage`, cookie, URL에 저장하지 않는다.
- 운영 환경에서는 HTTPS만 사용한다.
- secret key는 충분한 길이의 임의값이지만 API 부하 방어를 위한 rate limit은 추후 추가한다.
- 회원 시스템이 없으므로 분실한 secret key는 복구할 수 없다는 안내를 생성 결과와 관리 입력 영역에 표시한다.
- secret key 재발급 기능이 생기기 전까지는 현재 key를 변경하거나 폐기할 수 없다.

## 14. 완료 조건

- 사용자가 단축 URL 또는 code와 secret key를 입력해 링크를 조회할 수 있다.
- 조회 결과에 만료 여부와 시각, 누적 진입 수, 실제 이동 수가 표시된다.
- 올바른 secret key로만 원본 URL과 만료 시각을 변경할 수 있다.
- 만료 및 삭제 링크의 리다이렉트가 차단된다.
- 링크 삭제는 soft delete로 처리된다.
- secret key 원문은 DB, 로그, API 응답에 다시 노출되지 않는다.
- 관리 API 계약과 기존 생성 및 리다이렉트 회귀 테스트가 통과한다.
