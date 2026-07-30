# 링크 생성 API 현행 명세

## 1. 문서 목적

이 문서는 현재 저장소에 구현된 `POST /api/links`의 API 계약, 정책, 코드 구성과 검증 범위를 설명한다.

링크 생성 API는 원본 URL과 선택적인 만료 시각을 받아 다음 값을 생성한다.

- 6자리 Base62 단축 코드
- 외부 공개 주소 기준 단축 URL
- 관리용 secret key 원문
- 요청에서 지정한 만료 시각

secret key 원문은 생성 응답에서 한 번만 전달하고 DB에는 BCrypt 해시만 저장한다. 이후 조회·수정·삭제는 `docs/implement/02_manage_link_api.md`의 `code + secret key` 인증 방식을 따른다.

## 2. 현재 구현 상태

다음 항목이 구현되어 있다.

- `Link` JPA 엔티티와 `LinkRepository`
- 암호학적으로 안전한 임의 문자열 생성기
- Base62 문자 기반 6자리 code 생성
- `srrrg_sk_` 접두사를 사용하는 secret key 생성
- secret key BCrypt 해시 저장 및 비교
- URL과 만료 시각 검증
- code 중복 사전 확인과 최대 5회 재생성
- `POST /api/links`
- 공통 400 오류 응답
- OpenAPI 문서
- 메인 화면의 링크 생성 폼과 결과 모달
- 생성 정책 단위 테스트
- PostgreSQL 및 애플리케이션 Compose 실행 구성

생성 이후의 리다이렉트와 결과별 접근 이벤트 기록은 별도 코드로 구현되어 있다. 이 문서는 그 기능을 상세히 설명하지 않고 생성 API와 맞닿는 부분만 다룬다.

## 3. API 계약

### 3.1 요청

```http
POST /api/links
Content-Type: application/json
Accept: application/json
```

```json
{
  "originalUrl": "https://example.com/very/long/url",
  "expiresAt": "2026-12-31T14:59:59Z"
}
```

필드 정책:

| 필드 | 필수 | 정책 |
|---|---|---|
| `originalUrl` | 필수 | HTTP/HTTPS 절대 URL, 최대 2,048자 |
| `expiresAt` | 선택 | ISO 8601 시각, 현재보다 미래여야 함 |

`expiresAt`을 생략하거나 `null`로 전달하면 무기한 링크를 생성한다. 애플리케이션 내부 시간 타입은 `Instant`를 사용하며 PostgreSQL에는 `TIMESTAMPTZ`로 저장한다.

### 3.2 성공 응답

HTTP 상태는 `201 Created`를 사용한다.

```json
{
  "code": "aB3x9Q",
  "shortUrl": "https://srrrg.link/aB3x9Q",
  "secretKey": "srrrg_sk_xxxxxxxxx",
  "expiresAt": "2026-12-31T14:59:59Z"
}
```

- `code`는 항상 6자리 Base62 문자열이다.
- `shortUrl`은 `SRRRG_BASE_URL`과 code를 결합한다.
- `secretKey`는 응답을 만든 뒤 서버에서 다시 조회할 수 없다.
- `expiresAt`이 없는 경우 응답에서도 `null`이다.

### 3.3 입력 오류 응답

Bean Validation 실패와 URL·만료 시각 정책 위반은 `400 Bad Request`로 변환한다.

```json
{
  "code": "INVALID_REQUEST",
  "message": "HTTP 또는 HTTPS URL만 사용할 수 있습니다."
}
```

필드별 오류 배열은 제공하지 않고 첫 번째 검증 메시지만 반환한다.

예상하지 못한 오류는 다음 공통 응답을 사용한다.

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "서버 오류가 발생했습니다."
}
```

## 4. 생성 정책

### 4.1 단축 코드

```text
문자 집합: 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz
길이: 6
```

`SecureRandom`으로 문자를 선택한다. `LinkService`가 `existsByCode`로 중복을 확인하고 중복이면 새 code를 생성하며 최대 5회 시도한다.

DB의 unique 제약이 동시 요청에서 발생하는 최종 충돌을 방어한다. 현재 구현은 사전 확인 이후 `save` 시점에 발생한 unique 충돌을 재시도하지 않는다. 실제로 충돌이 관측되면 저장 단위 재시도를 추가한다.

### 4.2 Secret key

```text
접두사: srrrg_sk_
임의 문자열 길이: 43
문자 집합: 영문 대소문자, 숫자, -, _
```

`SecretKeyManager`는 원문과 BCrypt 해시를 함께 생성한다.

```text
API 응답: secret key 원문
Link 엔티티: BCrypt 해시
DB: BCrypt 해시
```

원문은 엔티티, DB, 애플리케이션 로그에 저장하지 않는다. 링크 관리 시에는 code로 링크를 찾은 뒤 `SecretKeyManager.matches`로 입력값과 저장된 해시를 비교한다.

### 4.3 URL

검증 순서는 다음과 같다.

1. 값이 null 또는 공백인지 확인
2. 길이가 2,048자 이하인지 확인
3. Java `URI`로 파싱
4. scheme이 `http` 또는 `https`인지 확인
5. host가 있는 절대 URL인지 확인
6. localhost, loopback, 명시적인 사설 IPv4 주소 차단
7. IPv6 loopback 차단

명시적으로 차단하는 주소 범위:

```text
localhost 및 *.localhost
0.0.0.0/8
10.0.0.0/8
127.0.0.0/8
172.16.0.0/12
192.168.0.0/16
::1
```

DNS 조회, 접속 가능 여부 확인, URL 정규화는 하지 않는다. 따라서 도메인의 DNS 재해석까지 막는 완전한 SSRF 방어가 아니다. 현재 서버는 원본 URL로 직접 요청하지 않으며, 이 검증은 내부 주소를 향하는 리다이렉터 생성을 줄이는 정책이다.

### 4.4 만료 시각

- 미입력 또는 `null`: 무기한
- `expiresAt > Instant.now()`: 허용
- 현재와 같거나 과거: 400

만료 여부는 리다이렉트 시점에도 다시 확인한다.

## 5. 요청 처리 흐름

`LinkService.create`는 다음 순서로 처리한다.

```text
1. originalUrl 정책 검증
2. expiresAt 미래 시각 검증
3. 중복되지 않은 6자리 code 생성
4. secret key 원문과 BCrypt 해시 생성
5. Link 엔티티 생성
6. LinkRepository.save
7. base URL과 code로 shortUrl 생성
8. secret key 원문을 포함한 CreateLinkResponse 반환
```

생성된 링크의 초기값은 다음과 같다.

```text
accessCount = 0
redirectCount = 0
deleted = false
trusted = false
createdAt = 저장 직전 현재 시각
updatedAt = 저장 직전 현재 시각
```

## 6. 코드 구성

```text
src/main/java/link/srrrg
├── common
│   ├── ApiErrorResponse.java
│   ├── GlobalExceptionHandler.java
│   └── util
│       └── SecureRandomStringGenerator.java
└── link
    ├── Link.java
    ├── LinkRepository.java
    ├── LinkService.java
    ├── LinkController.java
    ├── LinkCodeGenerator.java
    ├── SecretKeyManager.java
    ├── UrlValidator.java
    └── dto
        ├── CreateLinkRequest.java
        └── CreateLinkResponse.java
```

### `Link`

- `links` 테이블과 매핑하는 JPA 엔티티
- JPA용 protected 기본 생성자는 Lombok `@NoArgsConstructor`로 생성
- 외부 setter 없이 `Link.create` 정적 팩터리 사용
- `@PrePersist`, `@PreUpdate`로 감사 시각 갱신
- `Instant`를 PostgreSQL `TIMESTAMPTZ`에 매핑

현재 `links` 매핑 필드:

| Java 필드 | DB 컬럼 | 생성 초기값 |
|---|---|---|
| `id` | `id` | DB 생성 |
| `code` | `code` | 생성된 6자리 code |
| `originalUrl` | `original_url` | 요청 URL |
| `secretKeyHash` | `secret_key_hash` | BCrypt 해시 |
| `expiresAt` | `expires_at` | 요청값 또는 `null` |
| `accessCount` | `access_count` | `0` |
| `redirectCount` | `redirect_count` | `0` |
| `deleted` | `is_deleted` | `false` |
| `trusted` | `trusted` | `false` |
| `createdAt` | `created_at` | 저장 시각 |
| `updatedAt` | `updated_at` | 저장 시각 |

### `LinkRepository`

`JpaRepository<Link, Long>`를 상속한다. 생성 과정에서는 `existsByCode`와 `save`를 사용한다. `findByCode`와 접근·이동 수 증가 query는 리다이렉트 및 이후 관리 기능에서 사용한다.

### `SecureRandomStringGenerator`

지정된 문자 집합과 길이로 안전한 임의 문자열을 만든다.

```java
String generate(String characters, int length)
```

- 내부 `SecureRandom` 인스턴스를 재사용
- null 또는 빈 문자 집합 거부
- 1보다 작은 길이 거부
- Base62, 접두사, DB 중복 여부 같은 링크 정책은 알지 못함

### `LinkCodeGenerator`

Base62 문자 집합과 길이 6이라는 정책을 소유하고 실제 문자 선택은 `SecureRandomStringGenerator`에 위임한다.

### `SecretKeyManager`

- URL-safe 문자 집합으로 43자리 임의 문자열 생성
- `srrrg_sk_` 접두사 추가
- `BCryptPasswordEncoder`로 저장용 해시 생성
- `GeneratedSecretKey` record에 원문과 해시를 담아 반환
- `matches`로 관리 요청의 key 검증 지원

### `UrlValidator`

URL 길이, URI 형식, scheme, host와 명시적 내부 주소 차단 정책을 담당한다. URL을 실제로 호출하지 않고 검증을 통과한 원문을 그대로 저장한다.

### `LinkService`

URL·만료 검증, code 생성과 중복 확인, secret key 생성, 엔티티 저장 및 응답 생성을 담당한다. 구현체가 하나뿐이므로 별도 서비스 인터페이스는 두지 않는다.

### `LinkController`

- 기본 경로 `/api/links`
- `@Valid`로 요청 형식 검증
- 성공 결과를 `201 Created`로 반환
- Springdoc annotation으로 Swagger/OpenAPI 계약 제공
- 생성 규칙과 엔티티 변경 로직을 포함하지 않음

## 7. 데이터베이스 및 설정

운영 스키마는 Flyway가 관리하고 JPA는 `ddl-auto=validate`로 매핑을 검증한다. 이미 적용된 migration은 수정하지 않는다.

현재 생성 API와 직접 관련된 `links` 컬럼은 V1에서 생성되고, 이후 `trusted`, `redirect_count`, `access_count`가 각각 V4, V5, V10에서 정리됐다.

외부 단축 URL의 기준 주소는 다음 설정을 사용한다.

```yaml
srrrg:
  base-url: ${SRRRG_BASE_URL:https://srrrg.link}
```

`LinkService`는 마지막 `/` 하나를 제거한 뒤 `/{code}`를 붙인다.

Compose의 기본 개발값:

```text
SRRRG_BASE_URL=http://localhost:8080
```

따라서 로컬 생성 응답은 다음과 같은 주소를 반환한다.

```text
http://localhost:8080/aB3x9Q
```

## 8. 메인 화면 연동

`index.html`의 생성 폼은 다음 값을 전송한다.

```json
{
  "originalUrl": "사용자가 입력한 URL",
  "expiresAt": "선택한 시각을 UTC Instant 문자열로 변환하거나 null"
}
```

성공하면 결과 모달에 short URL과 secret key를 표시하고 각각 복사할 수 있게 한다. 모달을 닫거나 새 요청을 시작하면 표시된 secret key 문자열을 화면에서 비운다.

secret key는 다시 조회하거나 복구할 수 없으므로 생성 직후 안전하게 보관해야 한다는 안내를 유지한다.

## 9. 현재 자동 테스트

### `SecureRandomStringGeneratorTest`

- 요청 길이와 문자 집합 준수
- 빈 문자 집합 거부
- 0 이하 길이 거부

### `LinkCodeGeneratorTest`

- 길이 6과 Base62 문자 확인
- 반복 생성 결과가 항상 같은 고정값이 아닌지 확인

무작위 값의 완전한 유일성이나 통계적 분포는 테스트하지 않는다.

### `SecretKeyManagerTest`

- `srrrg_sk_` 접두사
- 해시에 원문이 포함되지 않음
- 생성된 원문과 해시가 BCrypt 검증을 통과

### `UrlValidatorTest`

- 공개 HTTP/HTTPS URL 허용
- 잘못된 URI, 상대 URL, host 없는 URL, 미지원 scheme 거부
- localhost, loopback, 사설 IPv4 거부
- 2,048자 초과 URL 거부

### `LinkServiceTest`

- 링크 저장과 secret key 원문 응답
- base URL의 마지막 `/` 제거
- 중복 code 재생성
- 과거 만료 시각 거부 및 저장 방지

`LinkControllerTest`가 기존 생성 API의 201 상태와 핵심 응답 필드를 회귀 검증한다. 실제 PostgreSQL 자동 통합 테스트는 없으며 서비스 테스트는 repository를 mock으로 대체한다.

## 10. 수동 확인

Compose 실행 후 다음 요청으로 확인할 수 있다.

```bash
curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com","expiresAt":null}'
```

확인 항목:

- HTTP 201
- code가 6자리 Base62 형식
- short URL이 `http://localhost:8080/{code}` 형식
- secret key가 `srrrg_sk_`로 시작
- DB에는 secret key 원문이 아닌 BCrypt 해시만 존재
- `access_count=0`, `redirect_count=0`
- `is_deleted=false`, `trusted=false`
- 단축 URL 접근 시 현재 리다이렉트 정책에 따라 처리

## 11. 알려진 보완 항목

다음은 현재 생성 API가 동작하기 위한 필수 조건은 아니지만 이후 보완할 수 있다.

- `save` 시점 unique 충돌의 제한된 재시도
- 잘못된 JSON 형식을 명시적인 400 JSON 응답으로 통일
- 생성 API의 입력 오류별 MockMvc 테스트 보강
- 실제 PostgreSQL 기반 통합 테스트
- IP 기반 생성 rate limit
- 도메인 차단 목록과 DNS 기반 주소 검증
- secret key 재발급 및 폐기

관리 API를 구현할 때 생성 API의 secret key 형식과 저장 방식은 변경하지 않는다.

## 12. 완료 기준

현재 구현은 다음 조건을 만족한다.

- 유효한 요청으로 링크를 생성하고 201을 반환한다.
- 잘못된 URL과 과거 만료 시각을 거부한다.
- 단축 코드는 6자리 Base62 문자열이다.
- code 중복을 최대 5회까지 사전 확인한다.
- secret key 원문은 생성 응답에만 포함하고 DB에는 BCrypt 해시를 저장한다.
- 환경별 base URL로 short URL을 생성한다.
- 메인 화면에서 생성 API를 호출하고 결과를 복사할 수 있다.
- 생성 정책 단위 테스트가 존재한다.
