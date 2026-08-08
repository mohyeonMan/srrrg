# 로그인·프로젝트·캠페인 구현 체크리스트

## 사용 규칙

- 단계 시작 전에 이 문서와 현재 코드를 비교한다.
- 코드가 존재하고 관련 테스트가 통과한 항목만 `[x]`로 표시한다.
- 문서 작성, 파일 생성 또는 일부 코드만으로는 완료 처리하지 않는다.
- 일부만 구현했으면 검증된 하위 항목만 체크하고 단계 완료는 비워 둔다.
- 구현 후 같은 변경에서 이 체크리스트를 반드시 갱신한다.
- 체크리스트와 코드가 다르면 추측해서 고치지 말고 사용자에게 알린다.

## 현재 구현 기준선

- [x] 비로그인 `POST /api/links` 링크 생성
- [x] 6자리 Base62 code 생성과 DB unique 제약
- [x] 관리용 `srrrg_sk_` secret key 1회 반환과 SHA-256 hash 저장
- [x] URL·만료 시각 검증
- [x] `X-Srrrg-Secret-Key` 기반 링크 조회·수정·soft delete
- [x] `GET /{code}` 302 리다이렉트와 404·410 처리
- [x] 익명 링크 URL 위험 검사와 결과별 접근 이벤트 기록
- [x] 누적 진입 수와 실제 이동 수 저장
- [x] 비로그인 홈과 management 화면
- [x] springdoc 기반 기존 익명 링크 OpenAPI
- [x] 기존 단위·HTTP 테스트 전체 통과 (`gradlew test`)
- [ ] 실제 상세 통계를 management 화면에 표시

## 1단계: OAuth와 JWT

- [x] Spring Security OAuth2 Client 추가
- [x] `users` migration과 엔티티·저장소
- [x] `oauth_accounts` migration과 `UNIQUE(provider, provider_user_id)`
- [x] `refresh_tokens`와 OAuth 연결 요청 저장 구조
- [x] Google 로그인과 callback
- [x] Kakao 로그인과 callback
- [x] GitHub 로그인과 callback
- [x] 공급자 이메일 없는 최초 가입·로그인 허용
- [x] 동일 verified email 충돌 시 기존 로그인 본인 확인
- [x] srrrg access JWT 발급·검증
- [x] HttpOnly·Secure cookie 적용
- [x] refresh token rotation과 재사용 탐지
- [x] CSRF 보호와 로그아웃
- [x] 기존 비로그인 경로 허용 회귀 검증
- [x] OAuth·JWT·refresh 통합 테스트
- [x] 1단계 완료 조건 충족

## 2단계: 프로젝트·멤버·초대

- [x] `projects`, `project_members` migration과 모델
- [x] 기본 프로젝트가 없는 로그인 사용자의 개인 프로젝트와 OWNER 생성
- [x] OWNER·EDITOR·VIEWER 권한 검사
- [x] 마지막 OWNER 제거·강등 차단
- [x] `project_invitations` migration과 token hash
- [x] 이메일 초대 발송·수락·취소·재발송
- [x] `/api/web/**` 프로젝트·멤버 endpoint
- [x] 프로젝트 slug·생성자·soft delete metadata migration과 모델
- [x] 프로젝트 상세·이름 수정·soft delete·overview endpoint
- [x] JWT 기반 프로젝트 단일 링크 생성과 생성자 기록
- [x] 기존 익명 링크의 프로젝트 귀속과 secret key 폐기
- [x] `POST /api/web/projects/{projectId}/links/{code}/claim`과 `X-Srrrg-Secret-Key` 계약
- [x] 타 프로젝트 접근 차단 통합 테스트
- [x] 기존 익명 링크 회귀 테스트
- [x] 2단계 완료 조건 충족

## 3단계: API key·공개 문서

- [x] `project_api_keys` migration과 모델
- [x] API key 원문 1회 표시와 SHA-256 hash 저장
- [x] scope·만료·폐기·최근 사용 시각
- [x] `/api/v1/**` API key 전용 인증
- [x] JWT cookie와 API key 인증 경로 분리
- [x] 타 프로젝트와 scope 밖 요청 차단
- [x] `links:write` API key 기반 프로젝트 링크 생성
- [x] API key 링크 생성 `Idempotency-Key` 재시도·충돌 처리
- [x] request ID와 신규 API 오류 형식
- [x] cursor pagination과 필요한 idempotency
- [x] 공개 OpenAPI group과 `/openapi.json`
- [x] srrrg 디자인의 `/docs/api`
- [x] 운영 Swagger UI 차단과 홈 링크 교체
- [x] API key·공개 계약 통합 테스트
- [x] 기존 익명 링크 회귀 테스트
- [x] 3단계 완료 조건 충족

## 4단계: 프로젝트 도메인

- [x] `project_domains` migration과 모델
- [x] `links.domain_id` 추가
- [x] 기존 전역 code unique 제약 제거 migration
- [x] 익명 `UNIQUE(code)` partial index
- [x] 프로젝트 `UNIQUE(domain_id, code)` partial index
- [x] 플랫폼 서브도메인 생성과 예약어 검사
- [x] 프로젝트 생성 시 플랫폼 서브도메인 자동 할당
- [x] OWNER의 플랫폼 서브도메인 변경과 기존 hostname 즉시 해제
- [x] wildcard DNS·TLS 배포 검증
- [x] Host + code 기반 리다이렉트 조회
- [x] 등록되지 않은 Host와 타 프로젝트 도메인 차단 테스트
- [x] 기존 `srrrg.link/{code}` 회귀 테스트
- [x] 4단계 완료 조건 충족

## 5단계: 캠페인·CSV

- [x] `campaigns` migration과 모델
- [x] link의 `campaign_id`, `external_id`, UTM 컬럼
- [x] 프로젝트 단일 링크와 캠페인 목록 분리
- [x] 캠페인 기본 목적지·UTM 기본값 동적 fallback
- [x] 캠페인 내 `external_id` unique 제약
- [x] 기존 query·fragment를 보존하는 UTM 병합
- [x] JSON batch와 idempotency
- [x] UTF-8 고정 template CSV upload
- [x] PostgreSQL 기반 비동기 import와 중복 실행 방지
- [x] import 진행 상태와 행별 오류 CSV
- [x] 필터된 링크 CSV export
- [x] UI·API·CSV가 같은 링크 생성 유스케이스 사용
- [x] 캠페인·CSV 통합 테스트
- [x] 기존 익명 링크 회귀 테스트
- [x] 5단계 완료 조건 충족

> 캠페인 기본 목적지와 UTM 기본값은 링크에 복사하지 않는다. 링크 자체 값이 없으면 리다이렉트할 때 현재 캠페인 값을 사용하며, 목적지가 둘 다 없으면 `410 Gone`을 반환한다.

## 6단계: 실제 통계

- [x] 기존 누적 통계를 management 화면의 실제 값에 연결
- [x] 링크 단위 기간·결과·유입·접속 환경 집계
- [x] 캠페인 단위 집계
- [x] 프로젝트 단위 집계
- [x] link·campaign·project 합계 일관성 검증
- [x] 샘플 통계 제거
- [x] 실제 데이터 기준 쿼리 성능 측정
- [x] 개인정보·보관 정책 적용
- [x] 통계 API·화면 통합 테스트
- [x] 기존 익명 링크 회귀 테스트
- [x] 6단계 완료 조건 충족

> 접근 수는 IP 중복 제거 없이 이벤트 행 수로 계산한다. 원시 IP·User-Agent·referer는 보관하지만 통계 응답에는 노출하지 않는다. 캠페인 링크 상태는 전체 기간의 비봇 접근 여부로 판정하고, 선택 기간 집계와 분리한다.
