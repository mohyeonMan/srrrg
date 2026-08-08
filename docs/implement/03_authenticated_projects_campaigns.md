# 로그인·프로젝트·캠페인 확장 구현 명세

## 1. 목적과 문서 관계

이 문서는 현재 익명 단축 링크 서비스를 유지하면서 다음 기능을 단계적으로 구현하기 위한 실행 명세다.

- Google, Kakao, GitHub OAuth 로그인
- srrrg 자체 JWT 기반 웹 인증
- 프로젝트, 멤버와 이메일 초대
- 프로젝트 API key와 공개 API 문서
- 프로젝트별 플랫폼 서브도메인
- 캠페인, UTM과 개인화 링크
- CSV 대량 import·export
- 링크·캠페인·프로젝트 통계

상위 제품·데이터 구조 결정은 `docs/architecture/project_campaign_personalized_links.md`를 따른다. 현재 구현된 익명 링크 계약은 `docs/implement/01_create_link_api.md`와 `docs/implement/02_manage_link_api.md`를 따른다.

## 2. 구현 전 필수 절차

### 2.1 반드시 읽을 문서

각 단계 구현자는 코드를 수정하기 전에 다음 문서를 처음부터 끝까지 읽는다.

1. `docs/conventions.md`
2. `docs/architecture/project_campaign_personalized_links.md`
3. `docs/implement/01_create_link_api.md`
4. `docs/implement/02_manage_link_api.md`
5. 이 문서
6. `docs/implement/05_implementation_checklist.md`

UI 변경 시에는 `docs/design/v2/*`도 읽는다.

`docs/performance/*`, k6 스크립트와 과거 성능 결과는 일반 기능 구현의 필수 읽기 자료가 아니다. 사용자가 성능 테스트나 성능 기준 변경을 명시적으로 요청한 경우에만 확인한다.

문서를 읽은 뒤 실제 코드와 Flyway migration을 확인한다. 오래된 계획 문서와 현재 코드가 다르면 현재 동작을 테스트로 확인하고, 기존 계약을 임의로 과거 계획에 맞추지 않는다. 구현 전에는 체크리스트와 코드를 대조하고, 구현 후에는 테스트로 확인된 항목만 같은 변경에서 체크한다.

### 2.2 질문 게이트

각 구현 단계 착수 직전에 다음을 수행한다.

1. 해당 단계의 미확정 정책을 목록으로 만든다.
2. 문서와 코드의 충돌, 빠진 보안·스키마·API·운영 결정을 찾는다.
3. 사용자에게 한 번에 질문하고 답을 받은 뒤 구현한다.

구현 중 새 미확정 사항을 발견하면 가장 그럴듯한 값으로 임의 보완하지 않는다. 데이터 소유권, 인증, 권한, 공개 API 계약, migration, 개인정보, 도메인 라우팅이나 운영 비용에 영향을 주는 결정은 작업을 멈추고 사용자에게 확인한다.

단순한 지역 변수명이나 기존 convention이 이미 답을 주는 사항까지 질문하지 않는다.

## 3. 확정 결정

### 3.1 인증

- 로그인 공급자는 Google, Kakao, GitHub다.
- 자체 비밀번호 로그인은 구현하지 않는다.
- 공급자 token을 srrrg 인증 token으로 사용하지 않는다.
- OAuth 성공 후 srrrg access JWT와 refresh token을 발급한다.
- 서버 세션과 sticky session을 사용하지 않는다.
- access JWT는 HttpOnly·Secure·SameSite=Lax cookie로 전달하며 기본 유효기간은 15분이다.
- refresh token도 HttpOnly·Secure·SameSite=Lax cookie로 전달하며 기본 유효기간은 30일이다.
- refresh token hash와 rotation 상태는 PostgreSQL에 저장한다.
- access JWT는 HS256으로 서명한다. 256bit 이상의 key를 배포 secret으로 주입하고 `kid`로 현재 key와 이전 검증 key를 구분한다. 이전 key는 access token 최대 수명 이후 제거한다.
- JWT `iss`는 `SRRRG_BASE_URL`, `aud`는 `srrrg-web`을 사용한다.
- 로그아웃은 refresh token을 즉시 폐기한다. access JWT denylist는 만들지 않고 최대 15분의 잔여 수명을 허용한다.
- 웹 JWT는 srrrg 웹 전용이다. 외부 API 인증에는 사용하지 않는다.
- 로그인 웹 JSON endpoint는 `/api/web/**`, 외부 자동화 endpoint는 `/api/v1/**`로 분리한다.

### 3.2 OAuth 계정 연결

- 내부 사용자는 `users` 한 행으로 표현한다.
- 로그인 수단은 `oauth_accounts`에 여러 행으로 연결한다.
- OAuth 계정 식별자는 이메일이 아니라 `UNIQUE(provider, provider_user_id)`다.
- 같은 이메일이라는 이유로 자동 병합하지 않는다.
- 동일한 verified email의 기존 사용자가 있으면 기존 로그인으로 본인 확인한 뒤 새 OAuth 계정을 연결한다.
- verified email이 없으면 `users.email`을 비워 둔 신규 사용자를 만들고 가입과 로그인을 허용한다. 로그인된 사용자가 새 OAuth 수단을 직접 연결하는 경우에도 공급자 이메일은 필수가 아니다.
- 공급자가 반환한 미검증 이메일은 `provider_email`에 저장할 수 있지만 대표 이메일이나 계정 연결 기준으로 사용하지 않는다.
- 이메일이 필요한 기능을 도입할 때 프로필 수정에서 이메일을 입력·검증받는다. 그전에는 해당 화면과 API를 미리 만들지 않는다.
- 이메일은 앞뒤 공백을 제거하고 `Locale.ROOT` 기준 소문자로 저장한다. 공급자별 점이나 `+` 주소 규칙은 적용하지 않는다.
- Google은 `openid`, `profile`, `email`, Kakao는 비즈 앱 권한 없이 제공되는 `profile_nickname`, GitHub는 `read:user`, `user:email` scope만 요청하되 이메일 제공을 가입 필수 동의로 취급하지 않는다.
- 공급자 access token과 refresh token은 로그인 완료 후 저장하지 않는다.

### 3.3 애플리케이션 구조

- 단일 Gradle 모듈과 단일 배포 애플리케이션을 유지한다.
- 기능별 패키지로 modular monolith 경계를 만든다.
- 독립 배포와 독립 확장이 실제로 필요해지면 해당 경계를 기준으로 서비스를 분리한다.
- 현재 단계에서 Gradle 멀티모듈, 별도 인증 서비스, API gateway나 message broker를 추가하지 않는다.

### 3.4 프로젝트와 도메인

- 프로젝트·멤버 단계 적용 후, 기본 개인 프로젝트가 없는 로그인 사용자에게 하나를 만든다.
- 프로젝트는 OWNER, EDITOR, VIEWER 멤버를 가진다.
- 미가입 사용자를 포함한 이메일 초대를 최초 범위에 포함한다.
- 프로젝트 slug는 lower-case DNS label 3~63자, 전체 unique, 생성 후 변경 불가로 둔다. 생략 시 `p-`와 영문 소문자·숫자 난수 8자리로 생성한다.
- `actuator`, `admin`, `api`, `app`, `auth`, `cdn`, `cname`, `dev`, `docs`, `help`, `login`, `mail`, `manage`, `oauth`, `oauth2`, `openapi`, `static`, `status`, `support`, `www` slug를 예약한다.
- 프로젝트 삭제는 `archived_at` 기반 soft delete로 처리하고 보관된 프로젝트의 링크와 API key 사용을 차단한다.
- 기존 프로젝트와 링크의 `created_by_user_id`는 nullable로 두고 신규 생성과 익명 링크 귀속부터 기록한다.
- 프로젝트마다 플랫폼 서브도메인 하나를 자동 생성한다.
- 플랫폼 서브도메인은 `{project.slug}.srrrg.link` 형식이다.
- 플랫폼 서브도메인은 wildcard DNS와 TLS 인증서를 공유한다.
- 프로젝트 링크 code는 `UNIQUE(domain_id, code)`다.
- 기존 익명 링크 code는 srrrg 기본 도메인 안에서 계속 unique다.
- 커스텀 도메인, CNAME 검증, 도메인별 인증서와 동적 Ingress 생성은 이번 개발에서 제외한다.

### 3.5 외부 API와 CSV

- 외부 자동화는 프로젝트 API key만 사용한다.
- API key는 `Authorization: Bearer srrrg_pk_...`로 전달한다.
- 웹 JWT와 외부 API key 인증 경로를 분리한다.
- API key 기본 만료는 없고 사용자가 만료일을 선택할 수 있다.
- 공개 API는 `/api/v1`을 사용한다.
- 원본 Swagger UI는 운영에 공개하지 않고 `/docs/api`와 `/openapi.json`을 제공한다.
- CSV는 고정 template, UTF-8, 비동기 import, 오류 CSV 다운로드로 시작한다.

## 4. 기존 기능 호환 기준

다음 동작은 로그인 기능 배포 후에도 바꾸지 않는다.

```text
GET  /
GET  /manage
POST /api/links
GET  /api/links/{code}
PATCH /api/links/{code}
DELETE /api/links/{code}
GET  /{code}
```

- 익명 링크 생성에는 로그인을 요구하지 않는다.
- 기존 `X-Srrrg-Secret-Key`를 계속 사용한다.
- 기존 익명 API DTO, HTTP status와 오류 body를 한꺼번에 Problem Details로 변경하지 않는다.
- 기존 code, secret hash, 접근 이벤트와 통계값을 migration 과정에서 재생성하지 않는다.
- 기존 Flyway 파일은 수정하지 않고 V11 이후 migration만 추가한다.
- 현재 `LinkController`, `LinkManagementService`, `RedirectService`를 새 구조에 맞춘다는 이유로 먼저 재작성하지 않는다.

새 Spring Security filter chain은 기존 공개 경로를 명시적으로 허용해야 한다. 보안 설정을 추가한 직후 익명 생성·관리·리다이렉트 회귀 테스트를 먼저 통과시킨다.

## 5. 모듈 경계

### 5.1 패키지 구성

```text
link.srrrg
├── link
│   ├── management
│   ├── redirect
│   ├── access
│   └── risk
├── identity
├── auth
│   ├── oauth
│   └── jwt
├── project
│   ├── member
│   ├── invitation
│   └── apikey
├── domain
├── campaign
│   └── importing
├── statistics
└── common
```

기존 `link` 하위 구조는 유지한다. 새 기능도 처음부터 `domain`, `application`, `infrastructure`, `adapter` 같은 동일한 계층 세트를 반복 생성하지 않는다.

### 5.2 의존 방향

- `identity`는 사용자와 OAuth 계정의 수명주기만 소유한다.
- `auth`는 OAuth callback, JWT와 API key 요청 인증을 HTTP 경계의 내부 인증 주체로 변환한다.
- `project`는 멤버십과 역할을 판단하며 JWT claim의 역할을 신뢰하지 않는다.
- `link`는 링크 생성·수정·리다이렉트 규칙을 소유하고 OAuth·JWT 구현을 알지 않는다.
- `domain`은 플랫폼 hostname 생성·예약어 검사와 Host 라우팅을 소유하고 링크 목적지나 UTM 규칙을 알지 않는다.
- `campaign`은 캠페인 기본값과 CSV import를 소유하며 링크 생성은 `link`의 하나의 생성 유스케이스를 호출한다.
- `statistics`는 기존 event와 link 관계를 읽기만 하며 다른 기능이 statistics에 의존하지 않는다.

서비스에는 가능한 한 `userId`, `projectId`, 명시적인 command를 전달한다. Spring Security의 `Jwt`, OAuth provider DTO와 Kubernetes resource 객체를 엔티티나 도메인 메서드에 전달하지 않는다.

### 5.3 interface를 허용할 경계

구현 교체 가능성만을 이유로 모든 클래스에 interface를 만들지 않는다. 다음처럼 외부 시스템과 맞닿거나 이미 둘 이상의 구현이 필요한 경계만 허용한다.

- 초대 메일 발송
- 기존 `UrlRiskChecker`

JWT 발급기, JPA repository와 단일 service는 구현이 하나인 동안 concrete class로 둔다.

## 6. 웹 인증 구현

### 6.1 OAuth 로그인 성공 흐름

```text
OAuth callback
→ (provider, provider_user_id) 조회
→ 기존 oauth_accounts가 있으면 user 확정
→ 없고 verified email도 없으면 email이 null인 신규 user 생성
→ verified email이 있고 같은 email의 user가 없으면 신규 user 생성
→ verified email이 있고 같은 email의 user가 있으면 자동 로그인·자동 병합 금지
→ 기존 로그인 본인 확인 화면으로 이동
→ 확인 성공 후 oauth_accounts 연결
→ srrrg access JWT와 refresh token 발급
```

Spring Security OAuth2 Client의 기본 HttpSession 기반 authorization request 저장소는 사용하지 않는다. OAuth `state`, S256 PKCE verifier와 로그인 완료 후 돌아갈 상대 경로는 10분 수명의 PostgreSQL 일회성 요청에 저장한다. 브라우저의 HttpOnly·Secure cookie에는 불투명 난수 원문만 전달하고 DB에는 해당 난수의 SHA-256 hash를 저장한다. 요청은 callback에서 한 번 사용한 뒤 삭제한다.

OAuth·JWT 단계에서는 사용자와 OAuth 계정 생성을 하나의 transaction으로 처리한다. 프로젝트·멤버 단계 적용 후에는 기본 프로젝트가 없는 사용자에게 프로젝트와 OWNER 멤버십을 같은 transaction으로 생성한다.

동일 이메일 충돌 중 새 OAuth 정보를 브라우저가 임의 변경할 수 없게 10분 수명의 일회성 연결 요청을 DB에 저장한다. 원문 확인 token은 HttpOnly·Secure cookie로 전달하고 DB에는 SHA-256 hash만 저장한다. 기존 로그인 성공 후 현재 사용자와 연결 대상 이메일을 다시 확인하고 OAuth 계정을 연결한다.

### 6.2 JWT와 refresh token

access JWT claim은 최소화한다.

```text
iss
aud
sub = 내부 user id
iat
exp
jti
kid
```

프로젝트 ID, 역할, 이메일과 API scope는 access JWT에 넣지 않는다. 변경 가능한 권한을 token 만료까지 고정하지 않기 위해서다.

refresh token은 `srrrg_rt_` 접두사와 URL-safe 난수 43자로 구성한 opaque random token으로 발급한다. JWT로 만들지 않고 SHA-256 hash만 저장한다.

```text
refresh_tokens
- token_hash
- token_family_id
- expires_at
- used_at
- revoked_at
- replaced_by_token_id
```

정상 refresh는 기존 token을 사용 처리하고 같은 family의 새 token을 발급한다. 이미 사용된 token이 다시 제출되면 탈취 가능성이 있으므로 family 전체를 폐기한다.

### 6.3 cookie와 CSRF

- token cookie에는 `HttpOnly`, `Secure`, `SameSite=Lax`를 적용한다.
- access cookie 이름은 `srrrg_access`, refresh cookie 이름은 `srrrg_refresh`로 하고 host-only cookie로 발급한다.
- access cookie path는 `/`, refresh cookie path는 `/api/web/auth`로 제한한다.
- 운영 환경에서 HTTPS가 아니면 token을 발급하지 않는다.
- 웹의 POST, PATCH, DELETE에는 CSRF token을 요구한다.
- CSRF cookie와 header는 `XSRF-TOKEN`, `X-XSRF-TOKEN`을 사용한다.
- 기존 익명 `/api/links/**`는 secret header를 브라우저가 자동 전송하지 않으므로 기존 계약을 보존하기 위해 CSRF 대상에서 제외한다.
- 외부 `/api/v1` API key 요청은 cookie를 인증 수단으로 읽지 않는다.
- access JWT를 localStorage나 URL에 저장하지 않는다.

### 6.4 로그아웃과 계정 상태

- 로그아웃은 현재 refresh token family를 폐기하고 cookie를 삭제한다.
- 모든 기기 로그아웃은 사용자의 모든 refresh token family를 폐기한다.
- `POST /api/web/auth/logout`은 현재 기기, `POST /api/web/auth/logout-all`은 모든 기기를 로그아웃한다. token 교체는 `POST /api/web/auth/refresh`를 사용한다.
- 이미 발급된 access JWT는 최대 15분 동안 유효할 수 있다.
- 즉시 access JWT 차단용 Redis·denylist는 초기 범위에서 제외한다.

## 7. 프로젝트, 멤버와 초대

### 7.1 권한

| 작업 | OWNER | EDITOR | VIEWER |
|---|---:|---:|---:|
| 프로젝트 설정 | O | X | X |
| 멤버·초대 관리 | O | X | X |
| API key 관리 | O | X | X |
| 링크·캠페인 변경 | O | O | X |
| 조회·통계 | O | O | O |

모든 프로젝트 유스케이스는 전달받은 `projectId`만 신뢰하지 않고 현재 사용자 멤버십을 확인한다. 마지막 OWNER 제거와 강등은 DB 변경 전에 차단한다.

### 7.2 이메일 초대

- 초대 가능 역할은 EDITOR와 VIEWER다.
- token 원문은 이메일 링크에서만 제공하고 DB에는 hash만 저장한다.
- 만료, 취소, 재발송과 수락을 지원한다.
- 재발송은 기존 token을 폐기하고 새 token을 발급한다.
- 이미 멤버인 이메일에는 새 초대를 만들지 않는다.
- 초대 수락 시 로그인하지 않았다면 OAuth 로그인 후 원래 초대 흐름으로 돌아온다.
- 초대 이메일은 연락처이며 계정 식별자나 권한 검증 수단으로 사용하지 않는다. 로그인한 사용자는 이메일 유무·일치 여부와 관계없이 유효한 초대 token을 수락할 수 있다.

메일 공급자별 구현은 하나의 메일 발송 경계 뒤에 둔다. 실제 공급자는 구현 전 질문 게이트에서 확정한다.

## 8. 프로젝트 API key

API key 형식은 다음과 같다.

```text
srrrg_pk_<public-prefix>_<secret>
```

- 원문은 생성 응답에서 한 번만 반환한다.
- secret은 기존 `SecureRandomStringGenerator`로 생성한다.
- 충분히 긴 임의 API key는 SHA-256 hash로 저장한다. 요청마다 느린 비밀번호용 해시를 계산하지 않는다.
- 목록에는 이름, public prefix, scope, 생성·최근 사용·만료·폐기 시각만 노출한다.
- 기본 만료는 없고 OWNER가 만료일을 선택할 수 있다.
- 교체는 새 key를 발급한 뒤 기존 key를 폐기하는 방식이다.

초기 scope:

```text
links:read
links:write
campaigns:read
campaigns:write
stats:read
```

API key에서 `projectId`와 scope를 결정한다. 요청 경로의 프로젝트가 다르면 거절한다. API key로 프로젝트 생성, 멤버 관리, 초대, 플랫폼 서브도메인 변경이나 다른 API key 관리를 허용하지 않는다.

## 9. 프로젝트 도메인과 라우팅

### 9.1 링크 주소

프로젝트를 만들 때 플랫폼 domain 하나를 자동으로 할당하고, 프로젝트 링크는 그 domain을 사용한다.

```text
project_id + domain_id + code
```

DB 제약:

```sql
UNIQUE (code) WHERE project_id IS NULL
UNIQUE (domain_id, code) WHERE domain_id IS NOT NULL
```

기존 전역 `links.code` unique 제약은 신규 migration에서 제거한다. 기존 익명 링크에는 `domain_id`를 채우지 않는다.

리다이렉트 조회는 Host와 code를 사용한다.

```text
srrrg.link + code
→ 기존 익명 링크 조회

acme.srrrg.link + code
→ project_domains 조회
→ domain_id + code로 프로젝트 링크 조회
```

Ingress가 전달하는 Host를 기준으로 하며 임의 `X-Forwarded-Host`를 신뢰하지 않는다. 신뢰할 proxy 범위와 forwarded header 처리는 배포 설정과 함께 검증한다.

### 9.2 커스텀 도메인 제외

커스텀 도메인 등록, CNAME 검증, 도메인별 인증서 발급과 동적 Ingress 생성은 이번 개발 범위에 포함하지 않는다. 플랫폼 wildcard DNS·TLS로 프로젝트 서브도메인만 제공하며, 커스텀 도메인은 실제 수요와 운영 방식을 확인한 뒤 별도 단계로 설계한다.

## 10. 캠페인과 CSV

### 10.1 캠페인 링크

- 단일 프로젝트 링크는 캠페인이 없어도 된다.
- 캠페인 링크는 프로젝트와 캠페인의 project가 같아야 한다.
- 캠페인 UTM 기본값은 링크 생성 시 복사한다.
- 링크 자체 목적지가 없으면 리다이렉트 시 현재 캠페인 기본 목적지를 사용하며, 둘 다 없으면 `410 Gone`을 반환한다.
- 캠페인 기본 목적지 변경은 자체 목적지가 없는 기존 링크에 즉시 반영한다.
- `external_id`는 캠페인 안에서 unique다.
- 이메일, 이름과 전화번호를 UTM이나 `external_id`에 직접 넣지 않도록 안내한다.

링크 생성 규칙은 UI, JSON batch와 CSV worker가 각각 구현하지 않는다. 모두 `link`가 소유한 동일한 프로젝트 링크 생성 유스케이스를 호출한다.

### 10.2 CSV import

초기 컬럼:

```text
original_url,external_id,utm_source,utm_medium,utm_campaign,utm_term,utm_content
```

- UTF-8 CSV만 받는다.
- 고정 template만 지원한다.
- 업로드 요청은 import ID를 즉시 반환한다.
- 원본 행, 상태와 오류는 bounded row 수 안에서 PostgreSQL에 저장한다.
- 오류 CSV는 저장된 행 번호와 오류 code로 생성한다.
- 자유 컬럼 매핑, Excel과 외부 cloud storage import는 제외한다.

k3s의 여러 pod가 같은 작업을 중복 처리하지 않도록 PostgreSQL row lock 또는 lease로 job 하나를 claim한다. message broker는 실제 적체와 독립 확장 필요가 확인된 뒤 검토한다.

## 11. 공개 API와 명세 페이지

### 11.1 인증 분리

```text
srrrg 웹
→ access JWT cookie
→ /api/web/**

외부 자동화
→ Authorization: Bearer srrrg_pk_...
→ /api/v1/**
```

`/api/web/**`는 API key를 읽지 않고 `/api/v1/**`는 JWT cookie를 읽지 않는다. 두 controller는 같은 application service를 호출하며 권한 확인 이후의 업무 규칙을 복제하지 않는다. 제3자 사용자 위임 OAuth authorization server는 초기 범위가 아니다.

### 11.2 경로와 호환

- 기존 익명 `/api/links`는 유지한다.
- 로그인 웹 화면의 JSON endpoint는 `/api/web`에 둔다.
- 새 공개 프로젝트 API는 `/api/v1`에 둔다.
- 새 API는 cursor pagination, request ID, idempotency와 안정적인 오류 code를 제공한다.
- 기존 익명 오류 응답은 호환성을 위해 유지하고 새 API부터 Problem Details를 적용한다.

프로젝트 단일 링크 생성은 JWT용 `POST /api/web/projects/{projectId}/links`와 API key용 `POST /api/v1/projects/{projectId}/links`가 같은 생성 service를 호출한다. API key 경로에는 `links:write` scope가 필요하며 선택적 `Idempotency-Key`의 동일 요청 재시도는 같은 링크를 반환한다. 프로젝트 도메인 단계 전에는 기존 전역 code와 기본 리다이렉트 경로를 사용한다.

### 11.3 문서

현재 springdoc, `OpenApiConfig`와 controller annotation을 OpenAPI 생성의 원천으로 유지한다.

```text
/docs/api       브랜드가 적용된 공개 안내·명세
/openapi.json   공개 계약
```

- 운영 `/swagger-ui/**`는 비활성화한다.
- `/v3/api-docs`는 내부 생성 원본으로 취급한다.
- 공개 계약에는 API key로 호출 가능한 endpoint와 기존 익명 API만 넣는다.
- OAuth callback, 웹 JWT 전용 계정·멤버·API key 관리, actuator와 운영자 API는 제외한다.
- endpoint와 schema는 OpenAPI에서 렌더링하고 인증 설명과 curl 예제만 직접 관리한다.
- 별도 문서 서버, SDK generator와 개발자 포털은 만들지 않는다.

## 12. 단계별 구현 순서

모든 단계는 구현 전후에 기존 익명 생성·조회·수정·삭제·리다이렉트와 management 화면 회귀 테스트를 통과해야 한다. 새 보안 filter, migration, 라우팅이나 DTO 변경이 기존 계약을 바꾸면 해당 단계는 완료되지 않은 것으로 본다.

### 1단계: OAuth와 JWT

- Spring Security OAuth2 Client 추가
- `users`, `oauth_accounts`, `refresh_tokens`와 OAuth 연결 요청 migration
- Google, Kakao, GitHub callback
- access JWT cookie, refresh rotation, CSRF와 로그아웃
- 이메일 없는 최초 가입·로그인 허용
- 동일 verified email 충돌의 기존 로그인 확인 흐름

완료 조건: 세 공급자로 하나의 내부 사용자에 로그인 수단을 연결할 수 있고, refresh 재사용과 로그아웃 정책이 통합 테스트로 검증된다.

### 2단계: 프로젝트와 멤버

- `projects`, `project_members`, `project_invitations`
- 기본 프로젝트가 없는 로그인 사용자의 개인 프로젝트 생성
- 역할별 권한
- 초대 발송·수락·취소·재발송
- 익명 링크 project 귀속

완료 조건: 기존 익명 링크를 해치지 않고 로그인 사용자가 프로젝트와 멤버를 관리한다.

### 3단계: API key와 공개 문서

- `project_api_keys`
- API key filter와 scope
- 필요한 `/api/v1` endpoint
- public OpenAPI group
- `/docs/api`, `/openapi.json`과 운영 Swagger 차단

완료 조건: 외부 요청은 JWT 없이 API key만으로 허용 범위의 프로젝트 작업을 수행하고 공개 문서와 실제 계약이 일치한다.

### 4단계: 프로젝트 도메인

- `project_domains`와 `links.domain_id`
- domain별 code unique migration
- Host + code 리다이렉트 조회
- 플랫폼 서브도메인
- wildcard DNS·TLS 배포 검증

완료 조건: 기존 `srrrg.link/{code}` 익명 링크와 프로젝트 도메인 링크가 충돌 없이 함께 동작한다.

### 5단계: 캠페인과 CSV

- `campaigns`와 link의 캠페인·UTM 필드
- 단일 링크와 캠페인 목록 분리
- JSON batch
- PostgreSQL 기반 CSV import worker와 오류 CSV

완료 조건: UI, API와 CSV가 같은 링크 생성 규칙을 사용하며 재시도로 중복 링크가 생기지 않는다.

### 6단계: 실제 통계

- 기존 누적 통계와 기간 통계를 관리 화면의 실제 이벤트에 연결
- link event를 link·campaign·project로 집계하고 캠페인 링크도 단일 링크 통계로 연결
- 빠른 기간과 사용자 지정 날짜, 일·월·년 추이, 결과, 유입과 접속 환경 조회
- 캠페인 링크 확인 상태와 UTM 값별 집계 제공
- 100,000 이벤트 기준 실제 쿼리 성능 측정

완료 조건: 샘플 통계를 제거하고 세 집계 범위의 합계가 일관된다.

## 13. Codex 실행 모델 가이드

모든 구현 단계는 `Sol / 중간`으로 실행한다. 단계별 모델 추천, 모델 변경 요청과 설정 확인은 구현 게이트로 사용하지 않는다.

실제 실행 시에는 `docs/implement/04_execution_prompt.md`의 프롬프트를 새 Codex task에 붙여 넣는다. 프롬프트는 다음 미완료 단계 판정, 질문 게이트와 한 단계 구현·검증까지만 수행하도록 제한한다.

전체 계획을 한 번에 실행하지 않는다. 단계 하나마다 다음 순서를 지킨다.

```text
다음 단계 판정
→ 관련 docs와 현재 코드 읽기
→ 구현 전 질문 게이트
→ 단계 하나 구현
→ 해당 테스트와 기존 익명 링크 회귀 테스트
→ 다음 단계
```

## 14. 테스트 원칙

- 정책 분기는 작은 단위 테스트로 검증한다.
- OAuth/JWT, 권한, migration, Host 라우팅과 API key는 HTTP·PostgreSQL 통합 테스트를 둔다.
- PostgreSQL 통합 테스트는 Testcontainers를 사용해 CI와 로컬 Docker 환경에서 같은 migration을 검증한다.
- 외부 메일 adapter만 경계에서 대체한다.
- service와 repository를 계층별로 모두 mock하는 테스트는 만들지 않는다.
- 각 단계마다 기존 익명 링크 회귀 테스트를 실행한다.

필수 보안 시나리오:

- 다른 provider가 같은 이메일을 반환해도 자동 병합되지 않음
- provider가 이메일을 반환하지 않아도 가입·로그인됨
- 기존 로그인 확인 후에만 OAuth 계정 연결
- 사용한 refresh token 재사용 시 family 폐기
- JWT에 들어 있지 않은 최신 프로젝트 역할 적용
- 타 프로젝트 API key 거절
- 폐기·만료 key 거절
- 초대 token 원문과 API key 원문이 DB·로그에 없음
- 등록되지 않은 플랫폼 Host와 타 프로젝트 도메인 거절
- 기존 익명 secret key와 프로젝트 API key의 권한 혼동 없음

## 15. 구현 전 남은 질문

다음 값은 해당 단계 구현 전에 사용자에게 확인한다.

1. API key·익명 링크·CSV의 실제 rate limit과 quota
2. JSON batch와 CSV 최대 행 수
3. 접근 이벤트와 IP·User-Agent 보관 및 익명화 기간
4. 계정 삭제 시 개인 프로젝트와 공동 프로젝트의 소유권 이전 정책

이 목록 외의 부족한 결정도 구현자가 발견하면 질문 게이트에 추가한다. 답을 받기 전에 임시 기본값으로 코드를 작성하지 않는다.

## 16. 의도적으로 제외한 것

- Gradle 멀티모듈
- 별도 인증 microservice
- sticky session, JDBC session과 Redis session
- 제3자 사용자 위임 OAuth server
- access JWT denylist
- API gateway
- message broker
- 자유로운 CSV 컬럼 매핑과 Excel import
- SDK 자동 생성
- 통계용 별도 저장소와 사전 집계
- 커스텀 도메인 등록과 CNAME 검증
- 도메인별 인증서 발급과 동적 Ingress 생성

실제 독립 배포, 처리 적체나 조회 성능 문제가 확인되면 `domain`, `campaign.importing`, `statistics` 경계를 우선 분리 후보로 검토한다.
