# srrrg v1 구성 및 구현 계획

## 1. 문서 목적

이 문서는 `docs/srrrg_v1_plan.md`의 요구사항을 현재 저장소에 어떻게 구성할지 정리한 실행 계획이다.

지금 단계에서는 코드를 구현하지 않는다. v1의 핵심인 링크 생성, 리다이렉트, secret key 기반 관리 기능을 실제 배포 가능한 최소 범위로 만드는 데 집중한다.

## 2. 현재 상태

- Java 21, Spring Boot 4.0.3, Gradle 기반 프로젝트 골격이 생성되어 있다.
- 기본 패키지는 `link.srrrg`이다.
- Actuator health probe 설정이 있다.
- Web, Validation, JPA, PostgreSQL 의존성은 아직 없다.
- 도메인 코드와 DB 마이그레이션은 아직 없다.
- Dockerfile은 있으나 애플리케이션 완성 후 실행 방식과 빌드 결과물을 다시 확인해야 한다.

## 3. v1 범위

### 포함

- 원본 URL과 선택적 만료일을 받아 6자리 단축 코드 생성
- 생성 시 관리용 secret key 1회 반환, DB에는 해시만 저장
- 단축 코드 접근 시 원본 URL로 302 리다이렉트
- secret key를 이용한 링크 조회, 수정, soft delete
- 만료 및 삭제된 링크 처리
- 접근 수 집계
- HTTP/HTTPS URL의 기본 검증
- PostgreSQL 스키마 마이그레이션
- 핵심 로직 및 API 테스트
- Docker 이미지와 k3s 배포 명세

### 제외

- 프론트엔드, 회원, 로그인, 팀 기능
- 커스텀 alias 및 서브도메인
- 상세 접근 통계와 별도 접근 이벤트 테이블
- Redis, 캐시, 메시지 큐, 비동기 접근 집계
- 관리자 화면과 악성 URL 외부 평판 조회
- IP별 rate limit

## 4. 먼저 확정할 정책

구현 전에 아래 값을 v1 정책으로 고정한다.

| 항목 | v1 결정 |
|---|---|
| 만료일 | 선택 입력, 미입력 시 무기한 |
| URL 최대 길이 | 2,048자 |
| 허용 스킴 | `http`, `https` |
| 단축 코드 | Base62 문자 6자리, 중복 시 재시도 |
| secret key | 충분한 길이의 난수에 `srrrg_sk_` 접두사 사용, SHA-256 해시 저장 |
| 리다이렉트 | `302 Found` |
| 없는 코드 | `404 Not Found` |
| 삭제·만료 링크 | `410 Gone` |
| 시간 저장 | PostgreSQL `TIMESTAMPTZ`, 애플리케이션에서는 `Instant` 사용 |
| 삭제 | `is_deleted = true`인 soft delete |

내부망 주소 차단은 URL의 호스트 문자열만 비교하면 DNS 우회가 가능하다. v1에서는 명시된 localhost, 루프백, 사설 IP 리터럴을 우선 차단하고, 도메인의 DNS 재해석까지 포함하는 완전한 SSRF 방어라고 표현하지 않는다. 서비스가 원본 URL에 서버 측 요청을 보내지는 않으므로 이 검증은 악성 리다이렉터 사용을 줄이는 수준으로 둔다.

## 5. 애플리케이션 구성

기능이 하나뿐이므로 계층을 과도하게 나누지 않고 `link` 패키지 안에 관련 코드를 모은다.

```text
src/main/java/link/srrrg
├── SrrrgApplication.java
├── link
│   ├── Link.java
│   ├── LinkRepository.java
│   ├── LinkService.java
│   ├── LinkController.java
│   ├── RedirectController.java
│   ├── LinkCodeGenerator.java
│   ├── SecretKeyManager.java
│   └── dto
│       ├── CreateLinkRequest.java
│       ├── CreateLinkResponse.java
│       ├── LinkDetailResponse.java
│       ├── UpdateLinkRequest.java
│       └── DeleteLinkRequest.java
└── common
    ├── ApiErrorResponse.java
    ├── GlobalExceptionHandler.java
    └── LinkException.java
```

별도의 `security`, `url`, `config` 패키지는 클래스가 실제로 늘어나기 전에는 만들지 않는다. URL 검증은 요청 DTO의 형식 검증과 `LinkService`의 정책 검증으로 시작한다. 예외도 종류별 클래스를 다수 만들기보다 오류 유형을 포함한 단일 도메인 예외로 시작한다.

## 6. 데이터 구성

### links 테이블

기획서의 단일 `links` 테이블을 유지한다.

- `id`: 내부 PK
- `code`: `VARCHAR(6)`, unique index
- `original_url`: URL 최대 길이에 맞춘 `VARCHAR(2048)`
- `secret_key_hash`: SHA-256 해시
- `expires_at`: nullable `TIMESTAMPTZ`
- `access_count`: 0부터 시작하는 `BIGINT`
- `is_deleted`: soft delete 플래그
- `created_at`, `updated_at`: 감사 시각

운영 DB 스키마를 JPA 자동 생성에 맡기지 않는다. Flyway 마이그레이션 한 개로 최초 테이블과 제약조건을 생성하고, JPA는 스키마 검증 모드로 사용한다.

접근 수는 `access_count = access_count + 1` 형태의 원자적 update query로 증가시킨다. 요청마다 엔티티를 조회하고 저장하는 방식보다 동시 요청에서 값 유실을 피하기 쉽다.

## 7. 요청 처리 흐름

### 링크 생성

1. JSON 형식과 필수값을 검증한다.
2. URL 스킴, 길이, 호스트 및 차단 대상 주소를 검증한다.
3. 만료일이 입력됐다면 현재보다 미래인지 확인한다.
4. 6자리 코드를 생성하고 DB unique 제약 충돌 시 제한된 횟수만 재시도한다.
5. secret key 원문을 생성하고 SHA-256 해시만 저장한다.
6. 저장 성공 후 secret key 원문을 포함한 응답을 반환한다.

### 리다이렉트

1. code 형식을 확인하고 링크를 조회한다.
2. 없으면 404, 삭제 또는 만료 상태이면 410을 반환한다.
3. 접근 수를 원자적으로 증가시킨다.
4. `Location` 헤더와 함께 302를 반환한다.

접근 수 증가 실패 시 리다이렉트까지 실패시킬지는 구현 시 테스트로 명확히 한다. v1 기본 방향은 저장 일관성을 위해 같은 요청 흐름에서 처리하는 것이다.

### 조회·수정·삭제

1. code로 링크를 조회한다.
2. 전달받은 secret key의 SHA-256 해시와 저장된 해시를 상수 시간 비교한다.
3. 인증 실패는 링크 존재 여부 노출을 줄이기 위해 공통 404 응답으로 처리한다.
4. 조회는 현재 정보를 반환한다.
5. 수정은 전달된 필드만 변경하고 URL 및 만료일 정책을 다시 검증한다.
6. 삭제는 `is_deleted`만 변경하며 반복 삭제 요청에는 410을 반환한다.

secret key는 조회 API의 query string에 넣지 않는다. URL과 접근 로그에 남는 것을 피하기 위해 관리 API 모두 `X-Srrrg-Secret-Key` 헤더로 통일한다. 따라서 기획서의 조회 API 형식은 아래와 같이 보정한다.

```http
GET /api/links/{code}
X-Srrrg-Secret-Key: srrrg_sk_xxxxxxxxx
```

PATCH와 DELETE도 같은 헤더를 사용하고 body에는 변경 데이터만 둔다.

## 8. 설정 및 의존성 계획

`build.gradle`에는 필요한 것만 추가한다.

- Spring Web
- Spring Data JPA
- Bean Validation
- PostgreSQL Driver
- Flyway PostgreSQL
- JDK `MessageDigest`의 SHA-256과 상수 시간 비교
- 테스트용 Testcontainers PostgreSQL

전체 Spring Security 웹 필터 체인은 로그인이나 세션 인증이 없는 v1에는 도입하지 않는다.

`application.yaml`은 공통 설정만 두고 DB 접속 정보와 외부 공개 URL은 환경 변수로 받는다.

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SRRRG_BASE_URL` (기본 운영값 `https://srrrg.link`)

민감한 기본 비밀번호나 운영 접속 정보는 저장소에 기록하지 않는다.

## 9. 구현 순서와 완료 기준

### 1단계: 기반 구성

- 필요한 의존성과 환경 변수 기반 설정 추가
- Flyway로 `links` 테이블 생성
- PostgreSQL Testcontainers 기반 통합 테스트 환경 준비

완료 기준: 빈 DB에서 마이그레이션이 적용되고 Spring context가 정상 기동한다.

### 2단계: 링크 생성

- 엔티티와 저장소 작성
- URL·만료일 검증, 코드 및 secret key 생성
- `POST /api/links` 구현

완료 기준: 유효한 요청은 DB에 저장되고 secret key 원문은 응답에만 존재한다. 잘못된 URL, 과거 만료일, 코드 충돌을 테스트한다.

### 3단계: 리다이렉트

- `GET /{code}` 구현
- 404/410 상태 구분
- 접근 수 원자적 증가 구현

완료 기준: 정상 링크는 302와 정확한 `Location`을 반환하고, 만료·삭제·없는 링크 및 동시 접근 증가를 테스트한다.

### 4단계: 관리 API

- secret key 검증
- 조회, 부분 수정, soft delete 구현
- 공통 오류 응답 정리

완료 기준: 올바른 key만 관리 작업을 수행하며, 수정 필드 재검증과 삭제 상태가 일관되게 적용된다.

### 5단계: 운영 준비

- Actuator health probe 확인
- Docker 이미지 빌드 및 비루트 실행 여부 확인
- k3s Deployment, Service, Ingress, Secret/ConfigMap 명세 작성
- graceful shutdown과 readiness/liveness probe 확인

완료 기준: 컨테이너가 환경 변수만으로 PostgreSQL에 연결되고, k3s에서 health probe 통과 후 HTTPS 도메인으로 요청할 수 있다.

## 10. 테스트 범위

### 단위 테스트

- Base62 6자리 코드 형식
- URL 허용/차단 정책
- 만료 판정
- secret key 생성 및 일치/불일치 검증

### 통합 테스트

- API 요청/응답 및 HTTP 상태 코드
- PostgreSQL unique 제약과 코드 충돌 재시도
- 생성 후 조회·수정·삭제 전체 흐름
- 삭제 및 만료 링크의 리다이렉트 차단
- 접근 수 증가

Mock 위주의 계층별 테스트를 대량 작성하지 않는다. 정책 로직은 단위 테스트하고, 실제 DB 및 HTTP 경계는 소수의 통합 테스트로 검증한다.

## 11. 구현 중 보류할 항목

다음 항목은 실제 필요나 트래픽 근거가 생길 때 결정한다.

- 코드 생성 재시도 횟수 조정
- 접근 증가 실패 시 리다이렉트 허용 여부
- rate limit 및 도메인 차단 목록
- DNS 조회 기반 사설망 호스트 차단
- 캐시와 비동기 접근 집계
- 링크 보존 및 물리 삭제 정책

## 12. 최종 점검 기준

v1 완료 여부는 다음 시나리오 하나로 확인한다.

1. PostgreSQL과 애플리케이션을 새 환경에서 기동한다.
2. 링크를 생성하고 응답의 code와 secret key를 보관한다.
3. 단축 URL에서 원본 URL로 302 이동하는지 확인한다.
4. secret key로 링크를 조회하고 URL과 만료일을 수정한다.
5. 링크를 삭제한 뒤 리다이렉트가 410을 반환하는지 확인한다.
6. health endpoint가 배포 환경에서 정상인지 확인한다.

이 시나리오가 자동 테스트와 배포 환경에서 통과하면 v1의 최소 범위를 완료한 것으로 본다.
