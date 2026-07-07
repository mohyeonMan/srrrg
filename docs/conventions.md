# srrrg 프로젝트 컨벤션

## 1. 커밋 메시지

### 형식

```text
타입 : 한글 요약

- 한글 상세 내용 1
- 한글 상세 내용 2
```

상세 설명이 필요 없는 단순 변경은 제목만 작성할 수 있다.

```text
docs : 프로젝트 컨벤션 문서 추가
```

### 타입

| 타입 | 용도 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `test` | 테스트 추가 및 수정 |
| `docs` | 문서 추가 및 수정 |
| `chore` | 일반 설정 및 기타 작업 |
| `build` | Gradle, Docker 등 빌드 변경 |
| `ci` | CI/CD 변경 |
| `perf` | 성능 개선 |

### 작성 규칙

- 타입과 요약 사이에는 ` : `를 사용한다.
- 요약은 한글로 간결하게 작성하고 끝에 마침표를 붙이지 않는다.
- 상세 내용 앞에는 빈 줄을 추가한다.
- 상세 내용은 `- `로 시작한다.
- 상세 내용에는 무엇을 변경했는지 구체적으로 작성한다.
- 한 커밋에는 하나의 논리적인 변경만 포함한다.
- `수정`, `작업`처럼 변경 내용을 알 수 없는 요약은 사용하지 않는다.

### 예시

```text
feat : 단축 URL 생성 기능 추가

- 6자리 Base62 단축 코드 생성
- 관리용 secret key 발급
- 중복 코드 발생 시 재생성
```

```text
fix : 만료된 링크의 리다이렉트 차단

- 만료 시각을 현재 시각과 비교
- 만료된 링크에 410 Gone 응답
```

## 2. 브랜치 이름

```text
<타입>/<간단한-영문-설명>
```

예시:

```text
feat/create-link
fix/expired-redirect
docs/conventions
chore/postgres-config
```

- `main` 브랜치는 빌드와 테스트가 통과하는 상태로 유지한다.
- 브랜치 설명은 영문 소문자와 하이픈을 사용한다.

## 3. Java 코드

- 기본 패키지는 `link.srrrg`를 사용한다.
- 클래스 이름은 `PascalCase`를 사용한다.
- 메서드와 변수 이름은 `camelCase`를 사용한다.
- 상수 이름은 `UPPER_SNAKE_CASE`를 사용한다.
- 의존성은 생성자 주입을 사용하고 필드 주입은 사용하지 않는다.
- API 응답에 JPA 엔티티를 직접 반환하지 않는다.
- 컨트롤러는 HTTP 요청과 응답을 처리하고 업무 규칙은 서비스에 둔다.
- 실제 필요가 생기기 전에는 불필요한 인터페이스나 추상 계층을 만들지 않는다.

## 4. API

- API 리소스 경로는 복수형을 사용한다. 예: `/api/links`
- JSON 필드 이름은 `camelCase`를 사용한다.
- 날짜와 시간은 ISO 8601 형식을 사용한다.
- 관리용 secret key는 `X-Srrrg-Secret-Key` 헤더로 전달한다.
- 오류 응답은 일관된 형식을 사용한다.

```json
{
  "code": "LINK_NOT_FOUND",
  "message": "링크를 찾을 수 없습니다."
}
```

## 5. 데이터베이스

- 테이블과 컬럼 이름은 `snake_case`를 사용한다.
- 스키마 변경은 Flyway migration으로 관리한다.
- 이미 적용된 migration 파일은 수정하지 않고 새 파일을 추가한다.
- 운영 환경에서 JPA의 `ddl-auto=create` 또는 `update`를 사용하지 않는다.
- 시간은 PostgreSQL `TIMESTAMPTZ`로 저장한다.
- secret key 원문은 저장하지 않는다.

Migration 파일 예시:

```text
V1__create_links_table.sql
V2__add_links_index.sql
```

## 6. 테스트

- 테스트 이름에는 검증할 행위와 기대 결과를 드러낸다.
- 정책 로직은 단위 테스트로 검증한다.
- HTTP 및 PostgreSQL 연동은 통합 테스트로 검증한다.
- private 메서드를 직접 테스트하지 않는다.
- 버그 수정 시 해당 문제를 재현하는 테스트를 추가한다.
- 커밋 전에 `gradlew test`가 통과하는지 확인한다.

```java
@Test
void expiredLinkReturnsGone() {
}
```

## 7. 설정 및 보안

- 비밀번호, secret key, 운영 DB 접속 정보를 Git에 커밋하지 않는다.
- 환경 변수 이름은 `UPPER_SNAKE_CASE`를 사용한다.
- 로그에 secret key와 전체 요청 헤더를 출력하지 않는다.
- 예제 설정에는 실제 값 대신 명확한 placeholder를 사용한다.

```text
SPRING_DATASOURCE_PASSWORD=change-me
```
