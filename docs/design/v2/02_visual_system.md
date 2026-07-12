# srrrg UIUX v2 - 비주얼 시스템

## 1. 브랜드

화면 표기는 `srrrg`를 기본으로 한다.

- 영문 소문자 유지
- 로고는 텍스트 로고로 유지
- 한국어 서비스명 `스르륵`은 문서나 소개 문구에서 보조적으로만 사용 가능
- 도메인 표기는 `srrrg.link`

## 2. 색상 토큰

현재 푸른색 메인 컬러를 유지하되, v2에서는 더 정돈된 토큰으로 사용한다.

| 토큰 | 값 | 용도 |
|---|---|---|
| `--color-brand` | `#2563eb` | 로고, 링크, 주요 액션 |
| `--color-brand-strong` | `#1d4ed8` | 선택된 탭, 보조 액션 텍스트 |
| `--color-brand-deep` | `#4f46e5` | 주요 버튼 gradient 보조색 |
| `--color-brand-soft` | `#eff6ff` | 보조 버튼, badge, 선택지 배경 |
| `--color-brand-soft-hover` | `#dbeafe` | 보조 버튼 hover |
| `--color-brand-border` | `#bfdbfe` | 보조 액션 테두리 |
| `--color-focus` | `rgba(37, 99, 235, 0.18)` | focus outline |
| `--color-text` | `#0f172a` | 기본 텍스트 |
| `--color-text-muted` | `#475569` | 본문 보조 텍스트 |
| `--color-text-subtle` | `#64748b` | label, description |
| `--color-text-placeholder` | `#94a3b8` | help, placeholder 성격 텍스트 |
| `--color-surface` | `#ffffff` | 기본 표면 |
| `--color-surface-glass` | `rgba(255, 255, 255, 0.92)` | 주요 패널 |
| `--color-surface-muted` | `#f8fafc` | 입력, URL 박스, 통계 박스 |
| `--color-surface-rail` | `#f1f5f9` | segmented control 배경 |
| `--color-border` | `#e2e8f0` | 기본 테두리 |
| `--color-border-strong` | `#dbe4ef` | 입력/탭 테두리 |
| `--color-danger` | `#b91c1c` | 삭제, 오류 텍스트 |
| `--color-danger-strong` | `#991b1b` | 강한 오류 텍스트 |
| `--color-danger-soft` | `#fef2f2` | 오류 배경 |
| `--color-danger-border` | `#fecaca` | 오류 테두리 |
| `--color-success` | `#047857` | 성공 메시지 텍스트 |
| `--color-success-soft` | `#ecfdf5` | 성공 배경 |
| `--color-success-border` | `#a7f3d0` | 성공 테두리 |
| `--color-warning` | `#b45309` | 주의 안내 텍스트 |
| `--color-warning-soft` | `#fffbeb` | 주의 안내 배경 |
| `--color-warning-border` | `#fde68a` | 주의 안내 테두리 |

색상 사용 규칙:

- 새 색을 추가하기보다 위 토큰을 재사용한다.
- 성공, 경고, 위험 상태는 의미별 색을 사용한다.
- 브랜드 파랑보다 강한 색은 주요 액션 외에 사용하지 않는다.
- 오류 화면도 버튼은 danger가 아니라 brand 또는 neutral 계열을 사용한다.

## 3. 배경

기본 배경은 기존 밝은 gradient를 유지한다.

```css
background:
  radial-gradient(circle at top left, rgba(37, 99, 235, 0.14), transparent 34rem),
  radial-gradient(circle at bottom right, rgba(79, 70, 229, 0.10), transparent 30rem),
  linear-gradient(180deg, #f8fbff 0%, #f8fafc 52%, #eef2ff 100%);
```

주의:

- gradient는 배경 분위기 용도다.
- 콘텐츠 가독성을 방해하면 안 된다.
- 빨간 radial gradient는 오류 전용 화면에서만 낮은 opacity로 사용한다.
- 별도 이미지나 장식 일러스트는 v2 범위에서 필수로 넣지 않는다.

## 4. 그림자

현재 shadow는 고급스러움보다 둥글고 귀여운 카드 인상이 강해질 수 있다. v2에서는 shadow를 한 단계 낮춘다.

| 토큰 | 값 | 용도 |
|---|---|---|
| `--shadow-panel` | `0 18px 50px rgba(15, 23, 42, 0.08)` | 주요 패널 |
| `--shadow-panel-hover` | `0 22px 60px rgba(15, 23, 42, 0.10)` | interactive card hover |
| `--shadow-action` | `0 12px 24px rgba(37, 99, 235, 0.22)` | primary button |
| `--shadow-action-hover` | `0 16px 30px rgba(37, 99, 235, 0.28)` | primary button hover |
| `--shadow-modal` | `0 30px 90px rgba(15, 23, 42, 0.26)` | modal |
| `--shadow-tab` | `0 6px 18px rgba(15, 23, 42, 0.07)` | selected tab |

## 5. Radius

v2에서는 약간 더 정제된 도구형 radius를 사용한다.

| 토큰 | 값 | 용도 |
|---|---:|---|
| `--radius-xs` | `8px` | 작은 badge 내부, compact field |
| `--radius-sm` | `10px` | 작은 버튼, 작은 선택지 |
| `--radius-md` | `12px` | URL 박스, secondary button |
| `--radius-lg` | `14px` | input, primary button |
| `--radius-xl` | `18px` | 모달, 작은 패널 |
| `--radius-2xl` | `20px` | 홈 주요 카드 |
| `--radius-pill` | `999px` | badge, copy pill |

기존 `28px` 큰 카드 radius는 v2에서 사용하지 않는다.

## 6. 타이포그래피

기본 폰트는 시스템 산세리프를 유지한다.

```css
font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
```

monospace가 필요한 값:

- secret key
- code
- API path
- raw URL을 기술적으로 보여주는 영역

```css
font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
```

타입 스케일:

| 이름 | Desktop | Mobile | Weight | 용도 |
|---|---:|---:|---:|---|
| Display | `48px` | `34px` | 900 | 홈 핵심 문구 |
| H1 | `32px` | `26px` | 850 | 확인/오류 페이지 제목 |
| H2 | `24px` | `22px` | 800 | 패널 제목 |
| H3 | `18px` | `17px` | 800 | 카드/섹션 제목 |
| Body | `16px` | `16px` | 400 | 일반 본문 |
| Body Large | `18px` | `16px` | 400 | 히어로 설명 |
| Label | `13px` | `13px` | 800 | form label |
| Caption | `12px` | `12px` | 700 | badge, 상태, 보조 설명 |
| Value | `26px` | `24px` | 900 | 통계 숫자 |

규칙:

- `letter-spacing`은 `0`을 기본으로 한다.
- 본문 `line-height`는 `1.6` 이상이다.
- Display는 `line-height: 1.08` 정도로 조밀하게 사용한다.
- 버튼은 `font-weight: 800`을 유지한다.
- 긴 URL은 글자 크기를 키우기보다 wrap과 max-height로 다룬다.

## 7. 레이아웃

홈:

```css
main {
  width: min(1120px, calc(100% - 32px));
  margin: clamp(28px, 6vw, 56px) auto;
}
```

확인/오류 화면:

```css
main {
  width: min(720px, calc(100% - 32px));
  margin: clamp(24px, 6vw, 48px) auto;
}
```

Desktop 홈 hero:

```css
.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(420px, 1.08fr);
  gap: 28px;
  align-items: start;
}
```

의도:

- 왼쪽: 짧은 가치 제안과 신뢰 요약
- 오른쪽: 실제 URL 생성 도구
- 생성 도구가 오른쪽에서 더 넓게 보이게 한다.

Tablet 이하:

```css
@media (max-width: 860px) {
  .home-hero {
    grid-template-columns: 1fr;
  }
}
```

Mobile:

```css
@media (max-width: 520px) {
  main {
    width: calc(100% - 20px);
    margin: 20px auto;
  }
}
```

Spacing scale:

| 토큰 | 값 |
|---|---:|
| `--space-1` | `4px` |
| `--space-2` | `8px` |
| `--space-3` | `12px` |
| `--space-4` | `16px` |
| `--space-5` | `20px` |
| `--space-6` | `24px` |
| `--space-7` | `28px` |
| `--space-8` | `32px` |
| `--space-10` | `40px` |
| `--space-12` | `48px` |

## 8. 공통 컴포넌트

### 8.1 Header

구성:

```text
srrrg        빠르고 안전한, 무료 단축 URL
```

Desktop:

- `display: flex`
- `align-items: baseline`
- `gap: 12px`
- padding: `20px 36px`
- 배경: `rgba(255, 255, 255, 0.72)`
- blur: `14px`
- 하단 border: `rgba(226, 232, 240, 0.75)`

Mobile:

- 세로 정렬
- gap `4px`
- padding `16px 20px`

추가 가능 항목:

- 우측에 `API` 텍스트 링크는 가능하지만 primary button처럼 보이면 안 된다.
- 로그인/회원가입은 v1/v2 범위에 없으므로 넣지 않는다.

### 8.2 Button

Primary button:

```css
color: #ffffff;
background: linear-gradient(135deg, #2563eb, #4f46e5);
border: 0;
border-radius: 14px;
font-weight: 800;
box-shadow: 0 12px 24px rgba(37, 99, 235, 0.22);
```

Primary 용도:

- `단축 URL 만들기`
- `원본 URL로 이동`
- `변경 저장`

Primary 상태:

| 상태 | 동작 |
|---|---|
| default | gradient, white text |
| hover | `translateY(-1px)`, stronger shadow |
| focus-visible | 3px brand outline |
| disabled/loading | opacity `0.68`, `cursor: wait`, text 변경 |
| active | transform 제거 또는 `translateY(0)` |

Secondary button:

```css
color: #1d4ed8;
background: #eff6ff;
border: 1px solid #bfdbfe;
border-radius: 12px;
font-weight: 800;
```

Secondary 용도:

- `돌아가기`
- `OpenAPI JSON`
- `전체보기`
- `닫기`

Danger button:

```css
color: #b91c1c;
background: #fff1f2;
border: 1px solid #fecdd3;
```

삭제는 단일 클릭 즉시 실행하지 않는다. 최소한 브라우저 confirm 또는 커스텀 확인 모달을 거친다.

### 8.3 Input

기본:

```css
width: 100%;
min-height: 48px;
padding: 12px 14px;
color: #334155;
background: #f8fafc;
border: 1px solid #dbe4ef;
border-radius: 14px;
font: inherit;
```

focus:

```css
outline: 3px solid rgba(37, 99, 235, 0.18);
border-color: #93c5fd;
background: #ffffff;
```

오류:

```css
outline: none;
border-color: #fecaca;
background: #fffafa;
```

입력 UX:

- URL input은 `type="url"`을 사용한다.
- 관리 secret key는 `type="password"`를 기본으로 한다.
- secret key 입력에는 `autocomplete="off"`, `spellcheck="false"`를 둔다.
- placeholder는 예시 역할만 하며 label을 대체하지 않는다.
- 오류 메시지는 input 아래에 둔다.

### 8.4 Expiration Selector

v2에서 select 대신 segmented choice를 사용한다.

```text
만료
[없음] [1시간] [하루] [1개월] [1년] [직접 입력]
```

상태:

| 상태 | 표시 |
|---|---|
| default | `없음` 선택 |
| selected | 흰색 배경, brand text, subtle shadow |
| hover | brand soft hover |
| disabled | opacity `0.55` |
| custom selected | datetime input 노출 |

마크업 권장:

```html
<fieldset class="expire-options">
  <legend>만료</legend>
  <button type="button" aria-pressed="true">없음</button>
  <button type="button" aria-pressed="false">1시간</button>
  <button type="button" aria-pressed="false">하루</button>
  <button type="button" aria-pressed="false">1개월</button>
  <button type="button" aria-pressed="false">1년</button>
  <button type="button" aria-pressed="false">직접 입력</button>
</fieldset>
```

개발 구현:

- 내부 상태 값은 기존 API와 동일하게 `expiresAt: Instant | null`로 변환한다.
- `없음` 선택 시 `expiresAt = null`.
- 빠른 선택은 현재 로컬 시각 기준으로 계산하되 API 전송 시 UTC instant로 변환한다.
- 사용자가 datetime 값을 직접 수정하면 선택 상태는 `직접 입력`으로 변경한다.

### 8.5 Result Panel

생성 성공 후 인라인으로 표시한다.

구성:

```text
단축 URL이 생성되었습니다

단축 URL
https://srrrg.link/aB3x9Q        [복사]

Secret key
srrrg_sk_xxxxxxxxx              [복사]

이 secret key는 다시 확인할 수 없습니다. 링크를 조회, 수정, 삭제하려면 지금 안전한 곳에 보관하세요.
```

상태:

| 상태 | 내용 |
|---|---|
| success | 단축 URL, secret key, copy buttons |
| copied | 버튼 텍스트 `복사됨`으로 1.5초 변경 |
| error | 오류 메시지와 재시도 가능 상태 |
| empty | 렌더링하지 않음 |

시각:

- 성공 패널은 과한 초록색으로 만들지 않는다.
- 전체 패널은 기본 surface를 유지한다.
- 상태 badge 정도만 success 색을 사용한다.
- secret key 안내는 warning soft 배경을 사용할 수 있다.

### 8.6 URL Display

기본:

```css
overflow-wrap: anywhere;
word-break: break-word;
background: #f8fafc;
border: 1px solid #e2e8f0;
border-radius: 12px;
padding: 12px 14px;
```

도메인 강조형:

```text
example.com
https://example.com/very/long/path?query=value
```

도메인:

- 크기: `20px`
- weight: `900`
- 색상: `#0f172a`

전체 URL:

- 크기: `14px`
- 색상: `#475569`
- monospace 사용 가능

### 8.7 Status Message

규칙:

- `role="status"`와 `aria-live="polite"`를 사용한다.
- 오류는 빨간색만 쓰지 말고 문구를 함께 제공한다.
- 메시지 영역은 layout shift를 줄이기 위해 최소 높이를 둔다.

문구 예:

| 상황 | 문구 |
|---|---|
| 생성 중 | `단축 URL을 만들고 있습니다...` |
| 생성 실패 | `단축 URL 생성에 실패했습니다. 입력한 URL을 확인하세요.` |
| 서버 연결 실패 | `서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.` |
| 조회 성공 | `링크 정보를 불러왔습니다.` |
| 변경 없음 | `변경된 값이 없습니다.` |
| 저장 성공 | `변경사항을 저장했습니다.` |
| 삭제 성공 | `링크를 삭제했습니다.` |
