# srrrg UIUX v3 - 비주얼 시스템 (대시보드 확장)

이 문서는 [v2 비주얼 시스템](../v2/02_visual_system.md)을 대체하지 않는다. 색상·타이포·radius·shadow 토큰은 v2를 그대로 상속하고, 여기서는 **워크스페이스 셸에서만 필요한 신규 컴포넌트**를 정의한다.

## 1. 신규 레이아웃 토큰

```css
:root {
  --sidebar-width: 240px;
  --sidebar-width-collapsed: 64px;
  --topbar-height: 56px;
  --drawer-width: min(480px, 100vw);
}
```

## 2. 사이드바

```css
.workspace-sidebar {
  width: var(--sidebar-width);
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  padding: var(--space-4) var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.sidebar-nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px var(--space-3);
  border-radius: var(--radius-md);
  color: var(--color-text-muted);
  font-weight: 700;
  font-size: 14px;
}

.sidebar-nav-item:hover {
  background: var(--color-surface-muted);
  color: var(--color-text);
}

.sidebar-nav-item[aria-current="page"] {
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
}
```

- 신규 색은 추가하지 않는다. active 상태는 v2의 `--color-brand-soft` / `--color-brand-strong` 조합을 그대로 사용 — 이미 v2 8.4절 Expiration Selector의 selected 상태와 동일한 논리다.
- 아이콘은 `16~18px`, `stroke-width: 1.75`의 단색 라인 아이콘을 권장한다(별도 라이브러리 없이 인라인 SVG로 8~10개만 필요: 개요/링크/캠페인/UTM/통계/멤버/설정/검색/닫기/체크). 아이콘 색은 텍스트 색을 상속(`currentColor`)해 active/hover 상태에서 별도 처리가 필요 없게 한다.

### 2.1 축소형 사이드바 (Tablet)

```css
@media (max-width: 1023px) {
  .workspace-sidebar {
    width: var(--sidebar-width-collapsed);
  }
  .sidebar-nav-item .sidebar-nav-label {
    display: none;
  }
}
```

라벨이 숨겨진 상태에서는 아이콘에 `title` 속성 또는 `aria-label`을 반드시 유지한다.

## 3. 상단 바 (Topbar)

```css
.workspace-topbar {
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: 0 var(--space-5);
  background: var(--color-surface-glass);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--color-border);
}
```

- `+ 만들기` 버튼은 primary button 스타일(v2 8.2)을 그대로 쓰되 상단 바 안에서는 높이를 `36px`로 compact하게 조정한다. 기존 `compact-action` 클래스(이미 `campaigns.html`, `statistics.html`에서 사용 중)를 재사용한다.
- 워크스페이스 스위처는 secondary button과 비슷하지만 테두리 없이 hover 시에만 `--color-surface-muted` 배경이 나타나는 "ghost" 스타일을 신규로 추가한다.

```css
.workspace-switcher > button {
  background: transparent;
  border: 0;
  border-radius: var(--radius-md);
  padding: 6px var(--space-3);
  font-weight: 800;
}
.workspace-switcher > button:hover {
  background: var(--color-surface-muted);
}
```

## 4. 데이터 테이블

현재 `campaign-link-table`, `activity-table` 두 종류의 테이블 스타일이 화면마다 조금씩 다르게 구현되어 있다. v3에서는 하나의 표준 `.data-table` 컴포넌트로 통일한다.

```text
[체크박스] 코드    목적지                  캠페인      상태     생성일       진입 수  실제 이동 수
  ☐        aB3x9Q  example.com/summer... 여름 세일   사용가능  2026-07-01     120        93
```

규칙:

- 헤더 행은 `position: sticky; top: 0;`로 스크롤 시 고정.
- 행 hover 시 `--color-surface-muted` 배경, 행 전체가 클릭 가능한 영역(단, 체크박스와 행 내 링크는 이벤트 전파를 막아 개별 동작 유지).
- 상태 컬럼은 텍스트만이 아니라 6장의 배지 컴포넌트를 사용한다.
- 정렬 가능한 컬럼은 헤더에 `▲▼` 표시, 현재 정렬 컬럼만 진하게.
- 숫자 컬럼(진입 수, 실제 이동 수)은 우측 정렬, `font-variant-numeric: tabular-nums`.
- 행 선택 체크박스 열은 "선택 삭제"처럼 일괄 작업이 있는 화면에만 노출한다(현재 `campaigns.html`의 `campaign-link-select-all` 패턴을 표준화).
- 빈 상태는 8장 참고.
- 100행이 넘어가면 무한 스크롤 대신 "더 보기" 버튼을 유지한다(현재 `load-more-*-button` 패턴이 이미 여러 화면에 있으므로 그대로 표준화).

```css
.data-table thead th {
  position: sticky;
  top: 0;
  background: var(--color-surface);
  font-size: 12px;
  font-weight: 800;
  color: var(--color-text-subtle);
  text-align: left;
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--color-border-strong);
}
.data-table tbody tr:hover {
  background: var(--color-surface-muted);
  cursor: pointer;
}
.data-table td {
  padding: var(--space-3);
  border-bottom: 1px solid var(--color-border);
  font-size: 14px;
}
.data-table td.numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
```

## 5. 드로어 (Slide-over Panel)

링크 상세처럼 "목록 맥락을 유지한 채 상세를 확인"하는 상황에 모달 대신 사용하는 신규 컴포넌트. v2가 "생성 결과는 모달보다 인라인"을 원칙으로 삼은 것의 대시보드 버전이다.

```css
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.32);
}
.drawer-panel {
  position: fixed;
  top: 0;
  right: 0;
  height: 100vh;
  width: var(--drawer-width);
  background: var(--color-surface);
  box-shadow: var(--shadow-modal);
  padding: var(--space-6);
  overflow-y: auto;
}
```

접근성 요구사항은 v2 5.4 Modal 규칙(role, focus trap, Escape, overlay 클릭 닫기, body scroll lock)을 드로어에도 동일 적용한다. 차이는 `role="dialog"` 대신 목록이 뒤에 계속 보이므로 `aria-modal="false"`를 쓰고, 배경 목록은 `inert` 또는 `aria-hidden`으로 스크린리더 접근만 차단한다.

용도:

- 링크 목록에서 행 클릭 → 링크 상세/미니 통계
- 멤버 목록에서 초대 상세(만료 재발송 등, 필요 시)

용도가 아닌 것(모달 유지):

- 삭제 확인 등 위험한 작업의 확인 다이얼로그는 기존처럼 `<dialog>` 기반 confirmation-dialog(이미 `management.html`에 구현됨)를 그대로 사용한다. 드로어는 "확인"용이 아니라 "탐색"용이다.

## 6. 상태 배지 확장

v2 8.7절의 status-badge를 다음 상태로 확장한다.

| 상태 | 색 | 사용처 |
|---|---|---|
| `사용 가능` | success | 링크 |
| `만료됨` | warning | 링크 |
| `삭제됨` | danger | 링크 (하드 삭제 아님, 이동 불가 상태를 의미) |
| `활성` | success | 캠페인 |
| `보관됨` | neutral(신규, 아래) | 캠페인 — soft delete지만 위협적이지 않은 상태이므로 danger가 아니라 중립색 |
| `대기 중` | warning | 초대 |
| `수락됨` | success | 초대 |
| `소유자` / `편집 가능` / `조회 전용` | neutral | 멤버 역할 |

기존 색 토큰에 `danger`/`success`/`warning`만 있고 "중립이지만 비활성"을 뜻하는 배지가 없어 하나 추가한다.

```css
:root {
  --color-neutral-soft: #f1f5f9;
  --color-neutral-border: #dbe4ef;
  --color-neutral-text: #475569;
}
.status-badge.neutral {
  background: var(--color-neutral-soft);
  border: 1px solid var(--color-neutral-border);
  color: var(--color-neutral-text);
}
```

`--color-neutral-*`는 v2의 `--color-surface-rail`/`--color-border-strong`/`--color-text-muted`와 사실상 같은 값이다. 새 색을 만들지 않고 기존 토큰을 별칭(alias)한 것뿐이다.

## 7. 카드형 핵심 지표 (프로젝트/캠페인 개요)

`statistics.html`의 `metric-grid`(6개 지표를 나란히)를 개요 화면에도 재사용하되, 개요에서는 3개 이하로 줄여 "한눈에 보이는 요약"에 집중한다.

```text
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│ 활성 링크      │ │ 활성 캠페인    │ │ 최근 7일 클릭  │
│ 128            │ │ 6              │ │ 4,301          │
│ +12 지난주 대비 │ │ +1             │ │ ▲ 8%           │
└───────────────┘ └───────────────┘ └───────────────┘
```

`metric-item`의 `dd`(값) 스타일은 v2 타이포 스케일의 `Value`(26px/900)를 그대로 사용한다.

## 8. 빈 상태 (Empty State)

현재 `project-empty`, `campaign-link-empty` 등 화면마다 문구만 다른 빈 상태가 있다. 표준 구조로 통일한다.

```html
<div class="empty-state">
  <p class="empty-state-title">아직 만든 링크가 없습니다</p>
  <p class="empty-state-description">이 프로젝트에서 첫 단축 링크를 만들어보세요.</p>
  <button class="primary-button">링크 만들기</button>
</div>
```

원칙:

- 아이콘/일러스트는 넣지 않는다(v2가 이미 "장난감처럼 보이는 화면"을 피하라고 명시했고, 이 원칙은 대시보드에도 동일 적용).
- 빈 상태에는 항상 지금 할 수 있는 행동(주로 "만들기" 버튼)을 함께 둔다. 설명만 있고 행동이 없는 빈 상태는 만들지 않는다.
- 필터링 결과가 0건인 경우("검색 결과 없음")는 위 컴포넌트와 구분한다 — 이 경우 CTA는 `필터 초기화`가 되어야 하며 "만들기" 버튼을 주지 않는다(이미 데이터는 있는데 필터가 숨긴 것이므로).

## 9. CSV 임포트 진행 상태

현재 `import-status-panel`이 텍스트 한 줄(`import-status-text`)로만 상태를 보여준다. 대량 작업이므로 진행 단계를 명시적으로 보여준다.

```text
업로드됨 ──● 처리 중 ──○ 완료
1,204 / 3,000행 처리 중...   [오류 CSV 다운로드 (12건)]
```

- 단계: `업로드됨` → `처리 중` → `완료` / `일부 실패` / `실패`
- 처리 중에는 진행률(진행된 행 / 전체 행)을 표시. 폴링 간격은 기존 JS 구현(`CampaignImportWorker` 폴링 로직)에 맞춰 결정.
- `일부 실패`는 danger가 아니라 warning 배지로 표시(전체 실패가 아니므로) — v2가 "정책적으로 확정된 악성 판정이 아닌 이상 과장하지 않는다"고 한 원칙과 같은 결의 판단이다.

## 10. 다크 모드

v3 범위에서는 다크 모드를 필수로 도입하지 않는다(질문 답변에서 v2 톤 계승이 우선순위로 선택됨). 다만 향후 추가를 막지 않기 위해 색상은 반드시 CSS 변수로만 참조하고 하드코딩된 hex 값을 컴포넌트 CSS에 직접 쓰지 않는다 — 이는 v2가 이미 원칙으로 갖고 있던 것과 동일하다.
