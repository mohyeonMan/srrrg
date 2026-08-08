# 5단계 캠페인·CSV 구현 실행 프롬프트

아래 내용을 다른 Codex 또는 AI 개발 task에 그대로 붙여 넣는다.

```text
srrrg 로그인·프로젝트·캠페인 확장 계획의 5단계인 캠페인·UTM 템플릿·JSON batch·CSV를 구현해줘.

이번 task에서는 5단계만 다룬다. 6단계 실제 통계 집계나 이후 기능은 구현하지 마라.

기준 문서:
- docs/conventions.md
- docs/architecture/project_campaign_personalized_links.md
- docs/architecture/campaign_utm_templates.md
- docs/implement/01_create_link_api.md
- docs/implement/02_manage_link_api.md
- docs/implement/03_authenticated_projects_campaigns.md
- docs/implement/05_implementation_checklist.md
- docs/design/v2/01_product_strategy.md
- docs/design/v2/02_visual_system.md
- docs/design/v2/03_screen_specs.md
- docs/design/v2/04_implementation_qa.md

문서 우선순위:
- UTM, 캠페인 UTM 기본값과 CSV 헤더·저장 구조는 docs/architecture/campaign_utm_templates.md가 최신 결정이다.
- 기존 문서의 고정 UTM 컬럼과 고정 CSV UTM 헤더가 새 문서와 다르면 새 문서를 따른다.
- 그 밖의 인증, 프로젝트, API key, 도메인과 익명 링크 계약은 기존 문서를 따른다.
- 문서 우선순위로 해결되지 않는 충돌은 임의 처리하지 말고 질문한다.

이미 확정된 제품·데이터 결정:
- UTM 템플릿은 프로젝트가 소유하며 프로젝트 안의 여러 캠페인이 공통으로 사용한다.
- utm_templates와 utm_template_fields를 별도 관계형 테이블로 만든다.
- JSONB, SQL 배열과 position 컬럼으로 UTM 관계를 표현하지 않는다.
- 캠페인은 현재 utm_template_id와 캠페인별 필드 기본값을 가진다.
- 링크는 생성 당시 utm_template_id를 snapshot으로 저장한다.
- link_utm_values가 링크와 템플릿 필드를 N:N 연결하면서 링크에 직접 지정한 값을 저장한다.
- 필드 추가는 해당 템플릿을 사용하는 캠페인의 새 양식에 반영하고, 기본값을 설정하면 값이 없는 기존 링크도 동적으로 상속한다.
- 필드 삭제는 soft delete이며 기존 링크 값, 리다이렉트와 과거 통계용 관계를 유지한다.
- 필드 이름 변경은 기존 필드 soft delete 후 새 필드 생성으로 처리한다.
- 캠페인 기본값 변경은 해당 필드 값을 직접 지정하지 않은 기존 링크에도 즉시 적용한다.
- 캠페인 기본 목적지와 UTM 기본값은 링크 자체 값이 없을 때 리다이렉트 시 동적으로 사용한다.
- 활성 UTM 필드는 템플릿당 최대 10개다.
- CSV는 original_url과 external_id 뒤에 현재 캠페인 템플릿의 활성 필드 이름을 헤더로 출력한다.
- CSV 필드 순서에는 의미가 없고 업로드는 헤더 이름으로 매핑한다.
- 캠페인 화면에서 현재 캠페인용 CSV 양식을 다운로드할 수 있어야 한다.
- external_id는 사용자가 입력하는 선택적 외부 참조 ID이며 캠페인 안에서 unique다.
- 이메일, 이름과 전화번호를 external_id나 UTM 값에 넣지 않도록 안내한다.
- 캠페인 삭제는 캠페인과 소속 링크를 함께 soft delete하고 기존 링크는 410 Gone을 반환한다.
- 캠페인 삭제 시 pending·processing import가 새 링크를 만들지 않게 중단한다.
- UI, 공개 API, JWT 웹 API와 CSV worker는 같은 링크 생성 유스케이스를 호출한다.
- UI는 테스트 도구가 아니라 실제 운영 가능한 캠페인·템플릿·링크·CSV 관리 화면으로 구현한다.

확정된 용량과 rate limit:
- JSON batch: 요청당 최대 500개
- CSV: UTF-8 또는 UTF-8 BOM, 최대 10,000행, 최대 10MB
- 동시에 처리하는 CSV import: 프로젝트당 1개
- 익명 링크 생성: IP당 분당 10회, 일 200회
- API key 조회: key당 분당 300회
- API key 링크 생성: key당 분당 60회
- JSON batch: 프로젝트당 분당 2회
- CSV upload: 프로젝트당 시간당 5회
- 대량 링크 생성: 프로젝트당 일 50,000개
- 초과 응답: 429 Too Many Requests와 Retry-After
- rate limit과 quota: Redis
- CSV job·row·error·idempotency: PostgreSQL
- message broker는 추가하지 않는다.

OAuth 이메일 정책:
- OAuth 최초 가입과 로그인에서 이메일을 필수로 요구하지 않는다.
- 이메일이 없어도 (provider, provider_user_id)로 사용자를 식별해 가입과 로그인을 허용한다.
- 이메일이 없다는 이유로 인증을 거절하거나 계정을 자동 병합하지 않는다.
- 이메일이 필요한 기능과 프로필 수정 API를 이번 단계에 미리 만들지 않는다.

먼저 읽기 전용으로 다음을 수행해라.
1. git status와 사용자 변경사항을 확인한다.
2. 위 기준 문서를 처음부터 끝까지 읽는다.
3. 현재 production code, 관련 test와 모든 Flyway migration을 확인한다.
4. 구현 체크리스트의 1~4단계를 코드와 테스트로 검증하고 5단계가 다음 미완료 단계인지 판정한다.
5. 체크리스트와 코드가 다르면 고치지 말고 차이를 보고한다.
6. 기존 익명 생성·관리·리다이렉트, 프로젝트 도메인과 API key 인증 흐름을 추적한다.
7. docs/architecture/campaign_utm_templates.md의 '구현 전에 남은 결정'과 새로 발견한 보안·스키마·API·운영 결정을 한 번에 질문한다.

질문 게이트:
- 이미 확정된 결정을 다시 질문하지 않는다.
- 질문이 하나라도 있으면 코드를 수정하지 말고 사용자 답을 기다린다.
- 질문이 없더라도 구현 범위와 제외 범위를 설명하고 구현 시작 승인을 한 번 받는다.
- 사용자 승인 뒤에만 구현한다.
- 명세에 없는 중요한 값을 임의 결정하지 않는다.

구현 범위:
- 기존 migration을 수정하지 않고 새 Flyway migration 추가
- 프로젝트 소유 UTM template과 field 모델·관리 API·운영 UI
- campaign 모델·CRUD·권한·운영 UI
- campaign별 UTM 기본값과 템플릿 선택·변경
- link의 campaign_id, utm_template_id, external_id와 관계형 UTM 값
- 프로젝트 독립 링크와 campaign 목록 분리
- 기존 query와 fragment를 보존하는 동적 UTM 병합
- campaign 단일 링크 생성과 JSON batch
- campaign별 CSV 양식 다운로드
- PostgreSQL 기반 비동기 CSV upload, 진행 상태, 행 오류와 오류 CSV
- 필터된 링크 CSV export
- Redis 기반 rate limit과 quota
- API key /api/v1과 JWT /api/web 인증 경계
- 공개 OpenAPI와 /docs/api 갱신
- 접근 가능한 실제 운영 UI
- 단위·HTTP·PostgreSQL 통합 테스트

제외 범위:
- 6단계 실제 통계 API와 통계 화면
- 통계용 별도 테이블, 사전 집계와 별도 저장소
- 커스텀 도메인, CNAME 검증, 도메인별 인증서와 동적 Ingress
- 자유로운 외부 CSV 컬럼 매핑과 XLSX
- cloud object storage와 message broker
- 제3자 사용자 위임 OAuth server
- 이메일 프로필 입력·검증 기능
- SDK generator와 별도 개발자 포털

구현 규칙:
- 기존 익명 POST /api/links, 관리 API, X-Srrrg-Secret-Key, management 화면과 리다이렉트를 보존한다.
- 기존 프로젝트별 플랫폼 서브도메인과 Host + code 라우팅을 보존한다.
- 캠페인·템플릿·링크의 project 일치를 DB 복합 FK와 application 권한 검사 양쪽에서 보장한다.
- 웹 endpoint는 JWT cookie와 CSRF만 사용하고 API key 인증을 읽지 않는다.
- 공개 /api/v1 endpoint는 API key만 사용하고 JWT cookie를 읽지 않는다.
- OWNER와 EDITOR만 변경하고 VIEWER는 조회만 허용한다.
- 기존 campaigns:read와 campaigns:write scope를 재사용한다.
- 링크 생성 규칙을 UI, 단일 API, batch와 worker에 복제하지 않는다.
- URL 형식과 내부 주소 차단 정책은 적용하되, 인증된 프로젝트 멤버와 API key가 만든 링크는 생성·리다이렉트 위험 검사를 생략한다.
- 입력 검증, 권한, idempotency, 동시성, 데이터 손실 방지와 접근성을 생략하지 않는다.
- 단일 Gradle 모듈과 기능별 package 경계를 유지한다.
- 구현체 하나에 불필요한 service·repository interface를 만들지 않는다.
- 후속 단계용 추상화와 테이블을 미리 만들지 않는다.
- 사용자 변경사항을 덮어쓰거나 되돌리지 않는다.
- 커밋과 push를 만들지 않는다.

Redis와 배포:
- srrrg 애플리케이션과 compose에 필요한 Redis 연결 설정을 추가한다.
- home-k3s-infra가 작업 공간에 있으면 srrrg dev·prod deployment와 namespaced Secret 참조를 함께 확인한다.
- Secret 원문, 공유 Redis 장애 정책이나 배포 방식이 확정되지 않았다면 질문하고 임의 값을 만들지 않는다.
- 개발과 운영 rate-limit key namespace를 분리한다.

구현 후:
1. 5단계 단위·HTTP·PostgreSQL 통합 테스트를 실행한다.
2. 기존 익명 링크 생성·조회·수정·삭제·리다이렉트 회귀 테스트를 실행한다.
3. 기존 프로젝트 링크, API key 인증, 프로젝트 도메인과 화면 회귀 테스트를 실행한다.
4. 공개 OpenAPI와 실제 endpoint가 일치하는지 검증한다.
5. 코드와 테스트로 검증된 항목만 docs/implement/05_implementation_checklist.md에서 [x]로 갱신한다.
6. 일부만 구현했으면 해당 하위 항목만 체크하고 5단계 완료는 체크하지 않는다.
7. 실패 원인을 해결하지 못하면 완료라고 보고하지 않는다.
8. 변경 파일, migration, 체크리스트, 테스트 결과, 남은 위험과 다음 단계를 보고한다.
9. 6단계 구현은 자동으로 시작하지 않는다.
```
