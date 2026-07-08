# 링크 생성 API 구현 계획

## 1. 목표

`POST /api/links` 요청으로 원본 URL을 등록하고 다음 값을 반환한다.

- 6자리 단축 코드
- `srrrg.link` 기준 단축 URL
- 관리용 secret key 원문
- 선택적으로 입력한 만료 시각

이번 구현은 링크 한 건을 정상적으로 생성하고 PostgreSQL에 저장하는 것까지만 완료한다.

## 2. 이번 범위

### 포함

- `Link` JPA 엔티티와 `LinkRepository`
- 암호학적으로 안전한 범용 임의 문자열 생성기
- Base62 문자 기반 6자리 무작위 코드 생성
- 관리용 secret key 생성과 BCrypt 해시 저장
- 원본 URL과 만료 시각 검증
- 링크 생성 서비스와 API
- 입력 오류에 대한 공통 400 응답
- 핵심 정책의 단위 테스트
- Compose 환경에서 실제 API와 DB 저장 확인

### 제외

- `GET /{code}` 리다이렉트
- 링크 조회·수정·삭제
- 클릭 수 증가
- Redis 또는 코드 캐시
- 별도 도메인 계층과 서비스 인터페이스
- 날짜, 문자열, 컬렉션 등을 한데 모은 목적 불명의 유틸리티 모음
- DNS 조회를 이용한 완전한 SSRF 방어
- Testcontainers와 테스트 전용 DB 환경

## 3. API 계약

### 요청

```http
POST /api/links
Content-Type: application/json
```

```json
{
  "originalUrl": "https://example.com/very/long/url",
  "expiresAt": "2026-12-31T14:59:59Z"
}
```

- `originalUrl`은 필수이다.
- `expiresAt`은 선택이며 미입력 또는 `null`이면 무기한이다.
- 애플리케이션 내부 시간 타입은 `Instant`를 사용한다.

### 성공 응답

HTTP 상태는 `201 Created`를 사용한다.

```json
{
  "code": "aB3x9Q",
  "shortUrl": "https://srrrg.link/aB3x9Q",
  "secretKey": "srrrg_sk_xxxxxxxxx",
  "expiresAt": "2026-12-31T14:59:59Z"
}
```

### 입력 오류 응답

HTTP 상태는 `400 Bad Request`를 사용한다.

```json
{
  "code": "INVALID_REQUEST",
  "message": "유효한 HTTP 또는 HTTPS URL을 입력해야 합니다."
}
```

필드별 상세 오류 목록은 v1 생성 API에 필요하지 않으므로 만들지 않는다.

## 4. 파일 구성

```text
src/main/java/link/srrrg
├── link
│   ├── Link.java
│   ├── LinkRepository.java
│   ├── LinkService.java
│   ├── LinkController.java
│   ├── LinkCodeGenerator.java
│   ├── SecretKeyManager.java
│   ├── UrlValidator.java
│   └── dto
│       ├── CreateLinkRequest.java
│       └── CreateLinkResponse.java
└── common
    ├── ApiErrorResponse.java
    ├── GlobalExceptionHandler.java
    └── util
        └── SecureRandomStringGenerator.java
```

구현체가 하나뿐인 `LinkService` 인터페이스는 만들지 않는다. DTO도 생성 API에 필요한 두 개만 만든다. 범용 유틸리티는 현재 실제 사용처가 있는 안전한 임의 문자열 생성 기능만 포함한다.

## 5. 클래스별 책임

### `Link`

`links` 테이블과 직접 매핑한다.

| Java 필드 | DB 컬럼 | 초기값 |
|---|---|---|
| `id` | `id` | DB 생성 |
| `code` | `code` | 생성된 6자리 코드 |
| `originalUrl` | `original_url` | 요청 URL |
| `secretKeyHash` | `secret_key_hash` | BCrypt 해시 |
| `expiresAt` | `expires_at` | 요청값 또는 `null` |
| `clickCount` | `click_count` | `0` |
| `deleted` | `is_deleted` | `false` |
| `createdAt` | `created_at` | 저장 직전 현재 시각 |
| `updatedAt` | `updated_at` | 저장 직전 현재 시각 |

구현 원칙:

- 기본 생성자는 JPA 용도로 `protected` 접근만 허용한다.
- 외부에서 필드를 임의 변경하는 setter는 만들지 않는다.
- 생성용 정적 팩터리 또는 필요한 값만 받는 생성자 하나를 둔다.
- Lombok은 추가하지 않는다.
- `Instant`는 PostgreSQL `TIMESTAMPTZ`에 매핑한다.

### `LinkRepository`

`JpaRepository<Link, Long>`를 상속한다.

생성 단계에서 필요한 추가 메서드는 다음 하나뿐이다.

```java
boolean existsByCode(String code);
```

### `SecureRandomStringGenerator`

`common.util` 패키지에 두고 지정된 문자 집합과 길이로 임의 문자열을 생성한다.

```java
String generate(String characters, int length)
```

- 내부 난수 생성에는 `SecureRandom` 하나를 재사용한다.
- 문자 집합이 비었거나 길이가 1보다 작으면 `IllegalArgumentException`을 던진다.
- 링크 코드, 임시 토큰처럼 문자 집합과 길이를 지정하는 기능에만 사용한다.
- Base62, 접두사, DB 중복 여부 같은 링크 도메인 규칙은 알지 못한다.
- static 메서드 모음으로 만들지 않고 생성 가능한 작은 객체로 둔다.

### `LinkCodeGenerator`

다음 62개 문자와 길이 6을 `SecureRandomStringGenerator`에 전달한다.

```text
0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz
```

역할은 Base62 문자 집합과 6자리라는 링크 코드 정책을 정하는 것이다. 실제 임의 문자 선택은 범용 생성기에 위임한다. DB 중복 확인과 재시도는 `LinkService`가 담당한다.

### `SecretKeyManager`

- `SecureRandomStringGenerator`에 URL-safe 문자 집합과 길이 43을 전달한다.
- 원문 앞에 `srrrg_sk_`를 붙인다.
- `BCryptPasswordEncoder`로 DB 저장용 해시를 만든다.

생성 결과는 원문과 해시를 함께 담은 내부 record로 반환한다. 원문은 API 응답을 만드는 동안만 사용하고 엔티티에는 해시만 전달한다.

### `UrlValidator`

다음 조건을 순서대로 확인한다.

1. 길이가 2,048자 이하인지 확인한다.
2. Java `URI`로 파싱 가능한지 확인한다.
3. 스킴이 `http` 또는 `https`인지 확인한다.
4. host가 존재하는 절대 URL인지 확인한다.
5. `localhost`, 루프백, 명시적인 사설 IPv4 주소를 차단한다.
6. IPv6 loopback인 `::1`을 차단한다.

DNS 조회는 하지 않는다. URL을 실제로 호출하거나 접속 가능 여부를 검사하지도 않는다. URL 문자열을 임의로 정규화하지 않고 검증을 통과한 원문을 저장한다.

### `LinkService`

링크 생성 흐름을 담당한다.

1. URL 정책을 검증한다.
2. 만료 시각이 있다면 현재 시각보다 미래인지 확인한다.
3. secret key 원문과 해시를 한 번 생성한다.
4. 단축 코드를 생성한다.
5. `existsByCode`로 중복 여부를 확인한다.
6. 중복이면 새 코드를 만들며 최대 5회 시도한다.
7. `Link` 엔티티를 저장한다.
8. 저장된 code와 secret key 원문으로 응답을 만든다.

DB unique 제약은 동시 요청에서 발생할 수 있는 최종 충돌을 막는다. 첫 구현에서는 매우 희박한 동시 충돌을 위한 복잡한 트랜잭션 재시도 구조를 만들지 않는다. unique 충돌이 실제로 관측되면 저장 재시도를 별도 작업으로 추가한다.

### `LinkController`

- 경로는 `/api/links`를 사용한다.
- `@Valid`로 요청 형식을 검증한다.
- 서비스 호출 결과를 `201 Created`로 반환한다.
- 검증, 코드 생성, 엔티티 생성 로직을 포함하지 않는다.

### 오류 처리

`GlobalExceptionHandler`는 이번 범위에서 다음 두 입력 오류만 400으로 변환한다.

- Bean Validation 실패
- URL 또는 만료 시각 정책 실패

예상하지 못한 DB 오류를 400으로 감추지 않는다. 별도 오류 코드 enum이나 예외 클래스 계층은 만들지 않는다.

## 6. 설정 변경

외부 단축 URL 생성을 위해 `application.yaml`에 한 항목을 추가한다.

```yaml
srrrg:
  base-url: ${SRRRG_BASE_URL:https://srrrg.link}
```

Compose의 `app` 서비스는 이미 개발값을 전달한다.

```text
SRRRG_BASE_URL=http://localhost:8080
```

설정 값은 현재 하나뿐이므로 별도 `@ConfigurationProperties` 클래스 대신 `@Value`로 주입한다. 설정이 늘어날 때 묶는 것을 검토한다.

## 7. 구현 순서

### 1단계: 영속성 매핑

- `Link` 엔티티 작성
- `LinkRepository` 작성
- 기존 `V1__create_links_table.sql`과 필드명·길이·nullable 조건 비교

완료 기준: 애플리케이션이 `ddl-auto=validate` 상태로 정상 기동한다.

### 2단계: 값 생성과 정책 검증

- `SecureRandomStringGenerator` 작성
- `LinkCodeGenerator` 작성
- `SecretKeyManager` 작성
- `UrlValidator` 작성
- 만료 시각 검증 기준 확정

완료 기준: DB 없이 핵심 정책 단위 테스트가 통과한다.

### 3단계: 생성 유스케이스

- 요청·응답 DTO 작성
- `LinkService` 생성 흐름 작성
- `LinkController`와 오류 응답 작성
- `SRRRG_BASE_URL` 설정 연결

완료 기준: 정상 요청이 201 응답과 함께 DB에 한 행을 저장한다.

### 4단계: 실제 환경 확인

- Compose로 `postgres`, `app` 실행
- 정상 및 실패 요청을 호출
- DBeaver 또는 `psql`로 저장값 확인

완료 기준: secret key 원문이 DB와 로그에 남지 않고 `secret_key_hash`만 저장된다.

## 8. 테스트 계획

자동 테스트는 DB가 필요 없는 정책부터 작성한다.

### `SecureRandomStringGeneratorTest`

- 요청한 길이만큼 생성하는지 확인
- 결과가 전달한 문자 집합으로만 구성되는지 확인
- 빈 문자 집합과 0 이하 길이를 거부하는지 확인

### `LinkCodeGeneratorTest`

- 항상 길이가 6인지 확인
- 모든 문자가 Base62 문자셋에 포함되는지 확인
- 여러 번 생성했을 때 단일 고정값만 반환하지 않는지 확인

무작위 결과의 완전한 유일성이나 분포는 테스트하지 않는다.

### `SecretKeyManagerTest`

- 원문이 `srrrg_sk_`로 시작하는지 확인
- 해시에 원문이 그대로 포함되지 않는지 확인
- 생성된 원문과 해시가 BCrypt 검증을 통과하는지 확인

### `UrlValidatorTest`

- 정상 HTTP/HTTPS URL 허용
- 상대 URL과 host 없는 URL 차단
- 허용하지 않은 스킴 차단
- 2,048자 초과 URL 차단
- localhost, loopback, 사설 IPv4 차단

### 수동 API 확인

```bash
curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com","expiresAt":null}'
```

확인 항목:

- HTTP 201인지 확인
- code가 6자리인지 확인
- shortUrl이 `http://localhost:8080/{code}`인지 확인
- secretKey가 응답에 한 번 포함되는지 확인
- `links` 행의 `secret_key_hash`가 BCrypt 문자열인지 확인
- `click_count=0`, `is_deleted=false`인지 확인

## 9. 완료 조건

- 유효한 요청으로 링크를 생성할 수 있다.
- 잘못된 URL과 과거 만료일은 400을 반환한다.
- 단축 코드는 6자리 Base62 문자로 생성된다.
- DB unique 제약과 애플리케이션 중복 확인이 함께 적용된다.
- secret key 원문은 응답에만 존재하고 DB에는 BCrypt 해시만 저장된다.
- Compose 환경에서 생성 API와 실제 PostgreSQL 저장을 확인한다.
- `gradlew test`가 통과한다.

## 10. 이후 작업

이 문서의 완료 조건을 만족한 뒤 별도 계획으로 `GET /{code}` 리다이렉트를 구현한다. 생성 API 구현 중 리다이렉트, 관리 API, 클릭 집계를 미리 추가하지 않는다.
