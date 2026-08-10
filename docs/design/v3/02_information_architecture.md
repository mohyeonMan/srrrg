# srrrg UIUX v3 - 정보 구조 (IA)

## 1. 전체 IA 트리

```text
/ (비회원 홈, 셸 없음, v2 그대로 유지)
/manage (비회원 링크 관리, 셸 없음, v2 그대로 유지)
/login

워크스페이스 셸 (사이드바 적용)
/projects                        - 워크스페이스 진입, 프로젝트가 없으면 생성 유도, 있으면 최근 프로젝트로 리다이렉트
/projects/{id}                   - 개요 (Overview)
/projects/{id}/links             - 링크 (전역 테이블)
/projects/{id}/campaigns         - 캠페인 목록
/projects/{id}/campaigns/{cid}           - 캠페인 상세 · 개요 탭
/projects/{id}/campaigns/{cid}/links     - 캠페인 상세 · 링크 탭
/projects/{id}/campaigns/{cid}/utm       - 캠페인 상세 · UTM 설정 탭
/projects/{id}/campaigns/{cid}/statistics - 캠페인 상세 · 통계 탭
/projects/{id}/utm-templates      - UTM 템플릿 (프로젝트 레벨, 독립)
/projects/{id}/statistics         - 프로젝트 통계 (캠페인 간 비교 포함)
/projects/{id}/members            - 멤버 및 초대
/projects/{id}/settings           - 프로젝트 설정 (이름, 서브도메인, 익명 링크 편입, 프로젝트 삭제)

/invitations/{token}              - 초대 수락 (셸 없음, 로그인 전/후 모두 진입 가능하므로 독립 유지)
```

현재는 `/projects`, `/campaigns`, `/statistics` 세 라우트가 프론트엔드 상태(쿼리 파라미터·JS 상태)로 모든 화면을 하나의 HTML 안에서 전환한다. v3는 URL 단위로 화면을 나눈다. 이는 브라우저 뒤로가기, 새로고침, 링크 공유가 자연스러워지는 이점이 있지만 **서버 라우팅(컨트롤러)에 새 경로 매핑이 필요**하다 — [05_implementation_qa.md](05_implementation_qa.md) 2장 참고.

## 2. 워크스페이스 셸 레이아웃

```text
┌────────────┬──────────────────────────────────────────────┐
│            │  Topbar: [프로젝트 스위처 ▾]      [검색] [+ 만들기] [계정] │
│  Sidebar   ├──────────────────────────────────────────────┤
│            │                                              │
│  개요      │                                              │
│  링크      │              Main Content                   │
│  캠페인    │                                              │
│  UTM 템플릿│                                              │
│  통계      │                                              │
│  ──────    │                                              │
│  멤버      │                                              │
│  설정      │                                              │
│            │                                              │
└────────────┴──────────────────────────────────────────────┘
```

- 사이드바는 항상 고정(sticky), 폭 `240px`(desktop)
- 상단 바는 사이드바 오른쪽 전체 폭, 높이 `56px`
- 사이드바와 상단 바는 모든 프로젝트 하위 화면에서 공통이며, 화면 전환 시 리로드되는 느낌 없이 메인 콘텐츠만 바뀐다는 인상을 준다(SPA-like 전환. 서버 렌더링이라면 최소한 사이드바/헤더는 fragment로 공유해 시각적으로 깜빡이지 않게 한다 — 현재도 `fragments/srrrg-layout.html`로 header/footer를 공유하는 패턴이 있으므로 동일한 방식을 사이드바에도 적용)

### 2.1 사이드바 구성

```html
<aside class="workspace-sidebar">
  <div class="workspace-switcher">
    <button aria-haspopup="listbox">
      <span class="workspace-switcher-name">마케팅팀</span>
      <span class="workspace-switcher-caret" aria-hidden="true"></span>
    </button>
  </div>

  <nav class="sidebar-nav" aria-label="프로젝트 메뉴">
    <a class="sidebar-nav-item" data-active="true">개요</a>
    <a class="sidebar-nav-item">링크</a>
    <a class="sidebar-nav-item">캠페인</a>
    <a class="sidebar-nav-item">UTM 템플릿</a>
    <a class="sidebar-nav-item">통계</a>
  </nav>

  <nav class="sidebar-nav sidebar-nav-secondary" aria-label="프로젝트 관리">
    <a class="sidebar-nav-item">멤버</a>
    <a class="sidebar-nav-item">설정</a>
  </nav>
</aside>
```

원칙:

- 상위 그룹(개요~통계)은 "이 프로젝트로 무언가를 하는" 메뉴, 하위 그룹(멤버, 설정)은 "이 프로젝트를 관리하는" 메뉴로 시각적으로 분리(구분선 + 약간의 여백)한다.
- 각 항목은 아이콘 + 텍스트. 아이콘이 없으면 목록이 다 비슷한 텍스트 링크로 보여 스캔이 느려진다. 최소한의 스트로크 아이콘 세트 도입을 권장한다([03_visual_system.md](03_visual_system.md) 7장).
- 현재 활성 화면은 `aria-current="page"` + `--color-brand-soft` 배경으로 표시(v2 토큰 재사용).
- VIEWER 권한 사용자에게는 `멤버`, `설정` 항목 자체를 숨긴다(있지만 비활성화하지 않는다 — 어차피 접근 못하는 메뉴를 보여줘 봤자 혼란만 준다).

### 2.2 워크스페이스 스위처

- 클릭 시 드롭다운: 최근 방문한 프로젝트 최대 5개 + 검색 입력(프로젝트가 많은 팀 대비) + 하단에 `새 프로젝트 만들기`
- 현재 `projects.html`의 좌측 프로젝트 목록(`project-sidebar`)이 여기로 흡수된다. 즉 "프로젝트를 고르는 화면"이 별도 페이지가 아니라 어디서든 열 수 있는 드롭다운이 된다.
- `/projects`(프로젝트 id 없이)로 접근하면: 최근 방문 프로젝트가 있으면 그 프로젝트의 개요로 리다이렉트, 없으면(첫 방문) 프로젝트 생성 온보딩 화면을 보여준다.

### 2.3 상단 바

```text
[프로젝트 스위처 ▾]              [🔍 링크 검색...]      [+ 만들기 ▾]   [계정 아바타 ▾]
```

- `+ 만들기` 드롭다운: `단축 링크`, `캠페인` 두 항목. 지금은 "링크 만들기"가 프로젝트 개요/캠페인 상세 등 여러 곳에 각자 폼으로 흩어져 있는데, 빠른 생성은 상단 바에서 전역으로 열리는 패널(또는 드로어)로 통일한다. 단, 캠페인 상세 화면 안에서 "이 캠페인에 링크 추가"처럼 맥락이 있는 생성은 해당 화면의 인라인 폼을 유지한다(전역 생성과 맥락 생성은 공존 가능).
- 검색은 코드/원본 URL/external_id를 대상으로 하며 결과는 `/projects/{id}/links?q=...`로 이동.
- 계정 아바타 드롭다운: 로그아웃, (향후) 계정 설정. v3 범위에서는 로그아웃만 필요.

## 3. 화면별 IA 상세

### 3.1 프로젝트 개요 (`/projects/{id}`) — 신규 화면

현재 존재하지 않는 화면이다. 로그인 후 첫 진입 지점이므로 "무엇을 할지"가 아니라 "지금 상태가 어떤지"를 요약한다.

```text
핵심 지표 카드: 활성 링크 수 · 활성 캠페인 수 · 최근 7일 클릭 수
최근 활동: 최근 생성된 링크 5개, 최근 완료/실패한 CSV 임포트
바로가기: 링크 만들기, 캠페인 만들기
```

### 3.2 링크 (`/projects/{id}/links`) — 신규 화면 (전역 링크 테이블)

지금은 프로젝트 화면의 "최근 링크" 목록과 캠페인 화면의 "캠페인 링크" 테이블이 서로 다른 컴포넌트로 따로 존재한다. v3에서는 하나의 링크 테이블 컴포넌트를 두 곳에서 재사용한다.

```text
필터: [전체 캠페인 ▾] [상태: 전체/사용가능/만료/삭제 ▾] [기간] [검색: 코드·URL·external_id]
[+ 링크 만들기]

테이블: 코드 | 목적지 | 캠페인 | 상태 | 생성일 | 진입 수 | 실제 이동 수
행 클릭 → 우측 드로어로 상세 오픈 (원본 URL, UTM, 만료, 미니 통계, 설정 바로가기)
```

캠페인 상세의 "링크" 탭(`/projects/{id}/campaigns/{cid}/links`)은 이 화면에 `캠페인=이 캠페인`이 고정된 뷰로, CSV 임포트/내보내기 액션이 추가로 붙는다.

### 3.3 캠페인 목록 (`/projects/{id}/campaigns`)

```text
[+ 캠페인 만들기]
카드 또는 행: 캠페인 이름 | 상태(활성/보관) | 링크 수 | 최근 7일 클릭 | 마지막 활동
```

보관된(soft delete) 캠페인은 기본적으로 목록에서 숨기고 `보관됨 보기` 토글로 노출한다.

### 3.4 캠페인 상세 (`/projects/{id}/campaigns/{cid}`) — 탭 구조

```text
캠페인 이름                                    [보관하기]
[개요] [링크] [UTM 설정] [통계]
```

| 탭 | 내용 | 현재 위치 |
|---|---|---|
| 개요 | 기본 목적지, 연결된 UTM 템플릿 요약, 핵심 지표 | 현재 `destination-title` 섹션과 중복 정보 |
| 링크 | 링크 테이블(3.2 컴포넌트 재사용) + 링크 생성 폼 + CSV 임포트/내보내기 | 현재 `create-campaign-link-form`, `campaign-link-table-wrap`, CSV 섹션 |
| UTM 설정 | 템플릿 선택/생성, 필드 관리, 캠페인 기본값 | 현재 `template-title`, `defaults-panel` 섹션 |
| 통계 | 캠페인 단위 통계 | 현재 `campaign-statistics-link`로 별도 페이지 이동하던 것을 탭으로 흡수 |

캠페인 삭제(보관)는 탭 바깥, 헤더 우측의 보조 액션으로 항상 보이되 primary 위계보다 낮게 둔다.

### 3.5 UTM 템플릿 (`/projects/{id}/utm-templates`) — 독립 화면 승격

```text
[+ 템플릿 만들기]
템플릿 목록: 이름 | 필드 수 | 사용 중인 캠페인 수
템플릿 선택 → 우측 또는 하단에 필드 편집 패널 (필드 추가/삭제, 최대 10개)
```

캠페인 상세의 "UTM 설정" 탭에서는 여기서 만든 템플릿을 **고르기만** 한다. 새 템플릿을 즉석에서 만드는 것도 허용하되(현재 `create-template-form`처럼), 그 경우에도 템플릿은 이 프로젝트 레벨 목록에 등록된다는 것을 명확히 한다.

### 3.6 통계 (`/projects/{id}/statistics`)

프로젝트 전체 관점: 캠페인 간 비교(`campaign-section`), UTM별 이동(`utm-section`), 링크 유입 상태(`link-status-section`)를 포함한다. 현재 `statistics.html`의 구성을 유지하되, 필터 위계와 탭 구조를 [04_screen_specs.md](04_screen_specs.md) 7장에서 재정리한다.

### 3.7 멤버 (`/projects/{id}/members`) — 독립 화면 승격

현재 `<details class="project-admin">` 안에 있던 멤버 목록·초대 폼·초대 대기 목록을 그대로 가져오되 페이지 레벨로 승격한다.

### 3.8 설정 (`/projects/{id}/settings`) — 독립 화면 승격

프로젝트 이름, 서브도메인, 익명 링크 편입(현재 `import-section`)을 일반 섹션으로, 프로젝트 삭제를 danger zone으로 분리한다.

## 4. 브레드크럼 규칙

캠페인 상세처럼 2단 이상 깊은 화면에는 상단 바 아래에 브레드크럼을 둔다.

```text
캠페인 / 2026 여름 세일 / 링크
```

- 첫 세그먼트(`캠페인`)는 목록으로, 두 번째(캠페인 이름)는 해당 캠페인의 `개요` 탭으로 링크.
- 마지막 세그먼트(현재 탭)는 링크가 아닌 텍스트.
- 사이드바 메뉴로 바로 이동 가능한 깊이 1 화면(개요, 링크, 통계 등)에는 브레드크럼을 넣지 않는다 — 사이드바 자체가 위치를 알려준다.

## 5. 반응형 IA

| Breakpoint | 사이드바 | 상단 바 |
|---|---|---|
| Desktop (`≥1024px`) | 고정 표시, 240px | 그대로 |
| Tablet (`768–1023px`) | 아이콘만 보이는 좁은 사이드바(64px)로 축소, hover/focus 시 라벨 툴팁 | 검색은 아이콘 클릭 시 확장 |
| Mobile (`<768px`) | 기본 숨김. 상단 바의 햄버거 버튼으로 전체화면 드로어 메뉴 오픈 | 워크스페이스 스위처와 `+ 만들기`만 상시 노출, 검색은 별도 화면/아이콘 |

캠페인 상세의 탭은 Mobile에서 가로 스크롤 가능한 세그먼트 형태(`console-tabs`와 동일 패턴, 기존 `management.html`의 `console-tabs` 구현을 그대로 재사용)로 유지한다.

## 6. 라우팅 변경 요약 (구현 참고용)

| 현재 | v3 | 비고 |
|---|---|---|
| `/projects` (전체 기능 단일 페이지) | `/projects`, `/projects/{id}`, `/projects/{id}/members`, `/projects/{id}/settings` | 서버 컨트롤러에 신규 매핑 필요 |
| `/campaigns?...` (쿼리로 캠페인 전환) | `/projects/{id}/campaigns`, `/projects/{id}/campaigns/{cid}`, 하위 4개 탭 경로 | path variable 기반으로 전환 |
| `/statistics?...` | `/projects/{id}/statistics` (프로젝트), `/projects/{id}/campaigns/{cid}/statistics` (캠페인) | 통계 API 자체는 기존 것 재사용 가능, 화면 라우팅만 분리 |
| 없음 | `/projects/{id}/links`, `/projects/{id}/utm-templates` | 신규 화면 |

API 엔드포인트(`/api/web/...`)는 이미 프로젝트/캠페인 단위로 잘 나뉘어 있어 변경이 필요 없다. 변경이 필요한 것은 **화면(Thymeleaf 라우팅)** 쪽이다.
