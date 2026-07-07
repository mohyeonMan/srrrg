# srrrg v1 기획 및 기술 구도

## 1. 프로젝트 개요

**srrrg**는 비회원 기반의 무료 단축 URL 서비스이다.

긴 URL을 입력하면 6자리 단축 코드를 생성하고, 사용자는 `srrrg.link/{code}` 형태의 짧은 URL을 사용할 수 있다.  
초기 버전에서는 화면 없이 API와 리다이렉트 기능 중심으로 구현한다.

```text
프로젝트명: srrrg
서비스명: 스르륵
도메인: srrrg.link
핵심 기능: 무료 단축 URL 생성 및 리다이렉트
```

---

## 2. v1 서비스 방향

### 기본 방향

```text
비회원 기반 API-only 단축 URL 서비스
```

사용자는 회원가입 없이 긴 URL을 등록할 수 있다.  
등록 시 단축 URL과 관리용 secret key가 함께 발급된다.

secret key는 링크 조회, 수정, 삭제 권한 확인에 사용한다.

### v1에서 하지 않는 것

```text
- 프론트엔드 화면
- 회원가입 / 로그인
- 조직 / 팀 기능
- 고급 통계 대시보드
- 커스텀 alias
- 커스텀 서브도메인
```

---

## 3. 주요 기능

## 3.1 단축 URL 생성

사용자가 원본 URL과 만료일을 입력하면 단축 URL을 생성한다.

```text
입력:
- originalUrl
- expiresAt

출력:
- shortUrl
- code
- secretKey
- expiresAt
```

예시:

```text
원본 URL:
https://example.com/very/long/url

단축 URL:
https://srrrg.link/aB3x9Q

관리용 secret key:
srrrg_sk_xxxxxxxxx
```

---

## 3.2 리다이렉트

사용자가 단축 URL에 접근하면 원본 URL로 이동한다.

```http
GET /{code}
```

처리 흐름:

```text
1. code로 링크 조회
2. 삭제 여부 확인
3. 만료 여부 확인
4. 클릭 수 증가
5. originalUrl로 302 redirect
```

---

## 3.3 링크 조회

secret key를 이용해 단축 URL 정보를 조회한다.

```http
GET /api/links/{code}?secretKey=...
```

조회 가능 정보:

```text
- code
- originalUrl
- shortUrl
- expiresAt
- clickCount
- createdAt
- updatedAt
```

---

## 3.4 링크 수정

secret key를 이용해 원본 URL 또는 만료일을 수정한다.

```http
PATCH /api/links/{code}
```

수정 가능 항목:

```text
- originalUrl
- expiresAt
```

---

## 3.5 링크 삭제

secret key를 이용해 링크를 삭제한다.

```http
DELETE /api/links/{code}
```

물리 삭제 대신 soft delete 방식으로 처리한다.

```text
isDeleted = true
```

---

## 4. 정책

## 4.1 단축 코드

```text
길이: 6자리
문자셋: 영문 대소문자 + 숫자
예시: aB3x9Q
```

사용 가능한 조합 수:

```text
62^6 = 약 568억 개
```

초기 서비스 규모에서는 충분하다.

중복이 발생하면 새 코드를 다시 생성한다.

---

## 4.2 커스텀 alias

v1에서는 지원하지 않는다.

예:

```text
srrrg.link/seoul
srrrg.link/campaign
```

이런 형태는 추후 기능으로 검토한다.

---

## 4.3 서브도메인

v1에서는 지원하지 않는다.

예:

```text
team.srrrg.link/aB3x9Q
campaign.srrrg.link/main
```

서브도메인 방식은 나중에 회원, 조직, 팀 기능이 생길 때 함께 검토한다.

필요한 요소:

```text
- *.srrrg.link 와일드카드 DNS
- Ingress 와일드카드 라우팅
- 서브도메인 중복 체크
- 예약어 차단
```

---

## 4.4 링크 만료

링크 생성 시 만료일을 설정할 수 있다.

기본 정책은 추후 결정한다.

가능한 옵션:

```text
- 사용자가 직접 만료일 입력
- 기본 무기한
- 기본 30일
- 기본 90일
```

현재 방향:

```text
생성 시 만료일 설정 가능
```

---

## 4.5 생성 제한

v1에서는 생성 제한을 두지 않는다.

다만 추후 악용 가능성이 생기면 아래 정책을 검토한다.

```text
- IP당 일일 생성 개수 제한
- 동일 URL 반복 생성 제한
- 특정 도메인 차단
- 요청 빈도 제한
```

---

## 5. 악성 URL 차단 정책

v1에서는 최소한의 URL 검증만 적용한다.

## 5.1 허용 스킴

허용:

```text
http
https
```

차단:

```text
javascript:
file:
ftp:
data:
mailto:
```

---

## 5.2 내부망 주소 차단

아래 주소로 향하는 URL은 차단한다.

```text
localhost
127.0.0.1
0.0.0.0
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
::1
```

목적:

```text
- SSRF 악용 방지
- 내부망 공격용 리다이렉터 방지
```

---

## 5.3 URL 길이 제한

너무 긴 URL은 차단한다.

예시 정책:

```text
최대 길이: 2,048자 또는 4,096자
```

---

## 6. 데이터베이스 설계

DB는 PostgreSQL을 사용한다.

## 6.1 links 테이블

```sql
CREATE TABLE links (
    id BIGSERIAL PRIMARY KEY,

    code VARCHAR(16) NOT NULL UNIQUE,
    original_url TEXT NOT NULL,

    secret_key_hash TEXT NOT NULL,

    expires_at TIMESTAMPTZ NULL,

    click_count BIGINT NOT NULL DEFAULT 0,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 6.2 필드 설명

| 필드 | 설명 |
|---|---|
| id | 내부 식별자 |
| code | 6자리 단축 코드 |
| original_url | 원본 URL |
| secret_key_hash | 관리용 secret key의 해시값 |
| expires_at | 링크 만료일 |
| click_count | 클릭 수 |
| is_deleted | 삭제 여부 |
| created_at | 생성일 |
| updated_at | 수정일 |

---

## 6.3 secret key 저장 방식

secret key는 원문 저장하지 않는다.

```text
사용자에게 보여주는 값: secretKey 원문
DB에 저장하는 값: secretKey hash
```

권장 방식:

```text
BCrypt 또는 SHA-256 + salt
```

MVP에서는 Spring Security의 BCryptPasswordEncoder 사용을 우선 검토한다.

---

## 7. API 명세 초안

## 7.1 단축 URL 생성

```http
POST /api/links
Content-Type: application/json
```

Request:

```json
{
  "originalUrl": "https://example.com/very/long/url",
  "expiresAt": "2026-12-31T23:59:59+09:00"
}
```

Response:

```json
{
  "code": "aB3x9Q",
  "shortUrl": "https://srrrg.link/aB3x9Q",
  "secretKey": "srrrg_sk_xxxxxxxxx",
  "expiresAt": "2026-12-31T23:59:59+09:00"
}
```

---

## 7.2 리다이렉트

```http
GET /{code}
```

Response:

```text
302 Found
Location: https://example.com/very/long/url
```

예외:

```text
404 Not Found: 존재하지 않는 code
410 Gone: 삭제 또는 만료된 링크
```

---

## 7.3 링크 조회

```http
GET /api/links/{code}?secretKey=srrrg_sk_xxxxxxxxx
```

Response:

```json
{
  "code": "aB3x9Q",
  "shortUrl": "https://srrrg.link/aB3x9Q",
  "originalUrl": "https://example.com/very/long/url",
  "expiresAt": "2026-12-31T23:59:59+09:00",
  "clickCount": 13,
  "createdAt": "2026-07-07T12:00:00+09:00",
  "updatedAt": "2026-07-07T12:00:00+09:00"
}
```

---

## 7.4 링크 수정

```http
PATCH /api/links/{code}
Content-Type: application/json
```

Request:

```json
{
  "secretKey": "srrrg_sk_xxxxxxxxx",
  "originalUrl": "https://new-example.com",
  "expiresAt": "2027-01-31T23:59:59+09:00"
}
```

Response:

```json
{
  "code": "aB3x9Q",
  "shortUrl": "https://srrrg.link/aB3x9Q",
  "originalUrl": "https://new-example.com",
  "expiresAt": "2027-01-31T23:59:59+09:00",
  "clickCount": 13
}
```

---

## 7.5 링크 삭제

```http
DELETE /api/links/{code}
Content-Type: application/json
```

Request:

```json
{
  "secretKey": "srrrg_sk_xxxxxxxxx"
}
```

Response:

```json
{
  "deleted": true
}
```

---

## 8. 기술 스택

## 8.1 Backend

```text
Java
Spring Boot
Spring Web
Spring Data JPA
PostgreSQL Driver
Validation
```

---

## 8.2 Database

```text
PostgreSQL
```

---

## 8.3 Frontend

v1에서는 제외한다.

API 테스트는 아래 도구를 사용한다.

```text
curl
Postman
IntelliJ HTTP Client
```

---

## 8.4 Deploy

현재 k3s 환경이 준비되어 있으므로, 아래 방식으로 배포한다.

```text
Spring Boot 앱
→ Docker 이미지 빌드
→ 이미지 레지스트리에 push
→ k3s Deployment 적용
→ Service 생성
→ Ingress로 srrrg.link 연결
→ PostgreSQL 연결
```

---

## 9. Docker / k3s 개념 정리

## 9.1 Docker

Docker는 애플리케이션을 실행 환경까지 포함해 컨테이너 이미지로 포장하는 도구이다.

```text
srrrg 서버 코드
+ Java 실행 환경
+ 설정
= Docker 이미지
```

---

## 9.2 VPS

VPS는 빌린 리눅스 서버이다.

예:

```text
AWS EC2
Oracle Cloud
Vultr
Hetzner
카페24 서버
```

현재 k3s 환경이 이미 있다면, VPS에 직접 jar를 띄우는 방식보다 컨테이너로 배포하는 방식이 자연스럽다.

---

## 9.3 k3s

k3s는 가벼운 Kubernetes 배포판이다.

srrrg에서는 다음 역할을 한다.

```text
- 애플리케이션 컨테이너 실행
- 서비스 포트 연결
- Ingress를 통한 도메인 연결
- 배포/재시작 관리
```

---

## 10. Spring Boot 패키지 구조 초안

```text
link.srrrg
 ├── SrrrgApplication.java
 ├── link
 │   ├── LinkController.java
 │   ├── RedirectController.java
 │   ├── LinkService.java
 │   ├── LinkRepository.java
 │   ├── Link.java
 │   ├── dto
 │   │   ├── CreateLinkRequest.java
 │   │   ├── CreateLinkResponse.java
 │   │   ├── LinkDetailResponse.java
 │   │   └── UpdateLinkRequest.java
 │   └── exception
 │       ├── LinkNotFoundException.java
 │       ├── LinkExpiredException.java
 │       └── InvalidSecretKeyException.java
 ├── security
 │   └── SecretKeyService.java
 ├── url
 │   └── UrlValidator.java
 └── common
     ├── ErrorResponse.java
     └── GlobalExceptionHandler.java
```

---

## 11. 구현 우선순위

## 11.1 1차 구현

```text
1. Spring Boot 프로젝트 생성
2. PostgreSQL 연결
3. links 테이블 생성
4. Link 엔티티 작성
5. 단축 코드 생성 로직 작성
6. secret key 생성 및 해시 저장
7. POST /api/links 구현
8. GET /{code} 리다이렉트 구현
```

---

## 11.2 2차 구현

```text
1. 링크 조회 API
2. 링크 수정 API
3. 링크 삭제 API
4. 만료 처리
5. 클릭 수 증가
6. URL 검증 강화
```

---

## 11.3 3차 구현

```text
1. Dockerfile 작성
2. Docker 이미지 빌드
3. k3s Deployment 작성
4. Service 작성
5. Ingress 작성
6. srrrg.link DNS 연결
7. HTTPS 적용
```

---

## 12. 추후 확장 후보

v1 이후 검토할 수 있는 기능이다.

```text
- 회원가입 / 로그인
- 회원별 링크 목록
- 클릭 통계
- 일별 클릭 수
- referer 통계
- 국가/브라우저 통계
- 커스텀 alias
- 서브도메인
- QR 코드 생성
- API key 발급
- 악성 URL 신고 기능
- 관리자 페이지
```

---

## 13. 현재 결론

srrrg v1은 다음 형태로 시작한다.

```text
회원가입 없이 바로 쓸 수 있는 API-only 단축 URL 서비스
```

핵심은 다음 세 가지이다.

```text
1. 긴 URL을 짧게 만든다.
2. 짧은 URL로 들어오면 원본 URL로 보낸다.
3. secret key로 조회, 수정, 삭제한다.
```

초기에는 기능을 크게 늘리지 않고, 실제로 배포 가능한 최소 버전을 완성하는 것을 우선한다.
