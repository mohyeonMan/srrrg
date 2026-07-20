# srrrg 디자인 컨벤션

## 1. 문서 목적

이 문서는 현재 srrrg 웹 화면에 적용된 디자인 규칙을 정리한다.

새 화면이나 컴포넌트를 추가할 때는 아래 규칙을 우선 따른다. 아직 별도 디자인 시스템이나 공통 CSS 파일은 없으므로, 현재 Thymeleaf 템플릿의 인라인 스타일을 기준으로 한다.

## 2. 전체 인상

- 밝고 가벼운 SaaS 도구 느낌을 유지한다.
- 핵심 색은 파란색 계열로 두고, 배경은 흰색과 옅은 slate, 옅은 indigo 톤을 사용한다.
- 화면은 마케팅 페이지보다 실제 링크 생성과 관리 작업을 바로 수행할 수 있는 도구형 UI에 가깝게 구성한다.
- 정보는 큰 히어로 문구, 작업 카드, 보조 기능 카드 순서로 읽히게 한다.
- 장식 요소는 과하게 늘리지 않고, 현재처럼 은은한 radial gradient와 soft shadow 수준으로 제한한다.

## 3. 색상

### 기본 색

| 용도 | 색상 |
|---|---|
| 본문 기본 텍스트 | `#0f172a` |
| 보조 텍스트 | `#475569`, `#64748b`, `#718096`, `#94a3b8` |
| 기본 배경 | `#f8fbff`, `#f8fafc`, `#eef2ff` |
| 패널 배경 | `rgba(255, 255, 255, 0.92)` |
| 입력 배경 | `#f8fafc` |
| 구분선 | `#e2e8f0`, `#dbe4ef`, `rgba(226, 232, 240, 0.9)` |

### 브랜드와 액션

| 용도 | 색상 |
|---|---|
| 브랜드/링크/주요 액션 | `#2563eb` |
| 진한 액션 텍스트 | `#1d4ed8` |
| 주요 버튼 gradient | `linear-gradient(135deg, #2563eb, #4f46e5)` |
| 보조 버튼 배경 | `#eff6ff` |
| 보조 버튼 hover | `#dbeafe` |
| 포커스 outline | `rgba(37, 99, 235, 0.15)` |

### 위험과 오류

| 용도 | 색상 |
|---|---|
| 오류/삭제 텍스트 | `#b91c1c`, `#dc2626`, `#991b1b` |
| 오류 배경 | `#fef2f2`, `#fff1f2`, `#fffafa` |
| 오류 테두리 | `#fecaca`, `#fecdd3` |

- 새 색을 추가하기보다 위 색상 조합을 재사용한다.
- 성공, 경고 등 새 상태 색이 필요할 때만 의미 단위로 추가한다.
- 같은 화면에서 브랜드 파랑보다 강한 색을 주요 액션 외에 사용하지 않는다.

## 4. 타이포그래피

- 기본 폰트는 시스템 산세리프를 사용한다.

```css
font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
```

- 로고는 `srrrg` 소문자 표기를 유지하고, 두껍고 짧게 보이도록 `font-weight: 900`을 사용한다.
- 제목과 버튼은 굵게 사용한다. 현재 주요 버튼과 탭은 `font-weight: 800`을 사용한다.
- 본문 설명은 `line-height: 1.6` 이상을 유지한다.
- `letter-spacing`은 기본값인 `0`을 유지한다.
- 긴 URL, secret key처럼 고정폭이 필요한 값은 monospace를 사용한다.

## 5. 레이아웃

- `body`는 최소 높이 `100vh`의 세로 flex 레이아웃을 사용한다.
- header와 footer는 `fragments/srrrg-layout.html`의 공통 fragment를 사용한다.
- 메인 콘텐츠는 중앙 정렬하고, 화면 폭에 따라 `width: min(..., calc(100% - ...))` 형태로 제한한다.
- 홈 화면의 기본 최대 폭은 `1040px`이다.
- 확인/오류 화면의 카드 최대 폭은 `640px`이다.
- 홈 화면의 상단 영역은 desktop에서 2열 grid를 사용하고, 좁은 화면에서는 1열로 접는다.
- 반복 카드 목록은 grid를 사용한다.
- 고정 높이가 필요한 작업 패널은 `clamp()`와 `svh`를 사용해 작은 화면에서도 스크롤 가능한 영역을 확보한다.

## 6. 표면과 카드

- 주요 패널은 흰색 반투명 배경, 옅은 slate 테두리, 큰 shadow를 함께 사용한다.

```css
background: rgba(255, 255, 255, 0.92);
border: 1px solid rgba(226, 232, 240, 0.9);
box-shadow: 0 24px 70px rgba(15, 23, 42, 0.10);
```

- 홈 화면의 큰 패널 radius는 `28px`를 기본으로 한다.
- 모바일에서는 큰 패널 radius를 `20px` 수준으로 줄인다.
- 확인/오류 화면의 단일 패널은 `clamp(18px, 4vw, 24px)` radius를 사용한다.
- 모달 패널은 `22px`, 모바일에서는 `18px` radius를 사용한다.
- 입력값, URL 박스, 통계 박스 같은 내부 표면은 `#f8fafc` 배경과 `#e2e8f0` 또는 `#dbe4ef` 테두리를 사용한다.

## 7. 버튼과 링크

- 주요 액션은 파란색 gradient 배경과 흰색 텍스트를 사용한다.
- 보조 액션은 옅은 파란 배경과 진한 파란 텍스트를 사용한다.
- 삭제 액션은 붉은 계열의 텍스트, 배경, 테두리를 사용한다.
- 버튼 radius는 대체로 `14px`를 사용한다.
- 탭 내부 버튼은 `12px` radius를 사용한다.
- pill 형태의 작은 badge나 복사 버튼은 `999px` radius를 사용한다.
- loading 상태에서는 버튼을 disabled 처리하고 `cursor: wait`, 낮은 opacity를 사용한다.
- hover가 있는 주요 버튼은 현재처럼 살짝 위로 이동하거나 shadow를 강화하는 정도로만 표현한다.

## 8. 입력 폼

- 입력 필드는 너비 `100%`, 배경 `#f8fafc`, 테두리 `#dbe4ef`, radius `14px`를 사용한다.
- focus 상태에서는 흰색 배경, 파란 테두리, 3px outline을 사용한다.
- label은 보조 텍스트 색과 굵은 글씨를 사용한다.
- 관련 입력은 grid로 묶고 `gap`을 명시한다.
- 긴 URL 입력과 출력은 줄바꿈이 깨지지 않도록 `overflow-wrap: anywhere`를 사용한다.
- secret key 입력은 화면에 오래 노출하지 않는 흐름을 유지한다.

## 9. 탭과 모달

- 탭 목록은 옅은 slate 배경의 segmented control 형태로 만든다.
- 선택된 탭은 흰색 배경, 파란 텍스트, 낮은 shadow로 표시한다.
- 탭은 `role="tablist"`, `role="tab"`, `role="tabpanel"`, `aria-selected`, `aria-controls`를 함께 사용한다.
- 모달 overlay는 어두운 slate 반투명 배경과 blur를 사용한다.

```css
background: rgba(15, 23, 42, 0.48);
backdrop-filter: blur(8px);
```

- 모달 패널은 화면 높이를 넘지 않도록 `max-height: calc(100svh - ...)`와 `overflow-y: auto`를 사용한다.
- 모달은 배경 클릭과 `Escape` 키로 닫을 수 있게 유지한다.

## 10. 반응형 기준

- 홈 화면은 `820px` 이하에서 1열 레이아웃으로 전환한다.
- `520px` 이하에서는 메인 여백, 카드 padding, 패널 radius, 버튼 배치를 모바일에 맞게 줄인다.
- `380px` 이하에서는 카드 padding과 header padding을 한 번 더 줄인다.
- 확인/오류 화면은 `520px` 이하에서 단일 컬럼과 전체 폭 버튼을 사용한다.
- `360px` 이하에서는 footer의 두 번째 문구를 다음 줄로 내리고 separator를 숨긴다.
- 모바일에서 주요 버튼은 가능한 한 `width: 100%`로 배치한다.

## 11. 접근성

- 문서 언어는 `lang="ko"`를 유지한다.
- viewport meta를 모든 페이지에 둔다.
- 동적 메시지는 `role="status"`와 `aria-live="polite"`를 사용한다.
- 모달은 `role="dialog"`, `aria-modal="true"`, `aria-labelledby`를 사용한다.
- 새 탭으로 열리는 링크는 `target="_blank"`와 `rel="noopener noreferrer"`를 함께 사용한다.
- 아이콘만 있는 버튼은 `aria-label`을 제공한다.
- 색상만으로 상태를 전달하지 말고 문구도 함께 바꾼다.

## 12. 문구

- 서비스명은 화면에서 `srrrg`로 표기한다.
- 사용자에게 보이는 문구는 한국어를 기본으로 한다.
- 버튼 문구는 짧은 동사형으로 작성한다. 예: `조회하기`, `변경 저장`, `링크 삭제`, `닫기`
- 보안 관련 문구는 명령형보다 안내형으로 쓴다.
- secret key는 `secret key` 표기를 유지한다.
- 위협 차단 화면은 잠재적 위험임을 분명히 하되 판정의 한계와 Google 출처를 함께 안내한다.

## 13. 현재 템플릿 기준

현재 디자인 규칙의 기준 파일은 아래와 같다.

- `src/main/resources/templates/index.html`: 홈, 링크 생성, 링크 관리, 결과 모달, 관리 모달
- `src/main/resources/templates/redirect-error.html`: 링크 없음, 만료, 위협 탐지, 검사 불가 등 리다이렉트 오류 화면
- `src/main/resources/templates/fragments/srrrg-layout.html`: 공통 header와 footer 마크업

공통 CSS 파일이 생기기 전까지는 위 파일의 스타일을 변경할 때 같은 컴포넌트가 다른 페이지에도 있는지 함께 확인한다.
