# srrrg UIUX v2 - 화면 명세

## 1. 홈 화면

### 1.1 목적

사용자가 회원가입 없이 긴 URL을 단축 URL로 만드는 일을 가장 빠르게 완료하게 한다.

### 1.2 주요 성공 기준

- 첫 방문자가 3초 안에 입력 위치를 이해한다.
- 생성 버튼이 첫 viewport 안에 있다.
- API 문서 링크보다 생성 폼이 시각적으로 강하다.
- 생성 후 secret key 보관 필요성을 놓치지 않는다.

### 1.3 Desktop 레이아웃

```text
┌──────────────────────────────────────────────────────────┐
│ Header                                                   │
├──────────────────────────────────────────────────────────┤
│ Main 1120px                                              │
│                                                          │
│ ┌──────────────────────┐  ┌────────────────────────────┐ │
│ │ Badge srrrg.link     │  │ 단축 링크 만들기           │ │
│ │ 긴 링크를 짧고       │  │ [URL input]                │ │
│ │ 안전하게             │  │ 만료 [없음][1시간]...      │ │
│ │ 설명 2줄             │  │ [단축 URL 만들기]          │ │
│ │ 신뢰 요약 2~3개      │  │ result panel              │ │
│ └──────────────────────┘  └────────────────────────────┘ │
│                                                          │
│ Trust feature row                                        │
│ Manage entry panel                                       │
│ API links                                                │
└──────────────────────────────────────────────────────────┘
```

### 1.4 Mobile 레이아웃

```text
srrrg
빠르고 안전한, 무료 단축 URL

srrrg.link
긴 링크를 짧고 안전하게
URL을 입력하면 단축 링크와 관리용 secret key를 바로 발급합니다.

[URL input]
만료
[없음] [1시간] [하루]
[1개월] [1년] [직접 입력]
[단축 URL 만들기]

result panel

내 링크 관리
...
```

### 1.5 권장 홈 카피

H1:

```text
긴 링크를 짧고 안전하게
```

Description:

```text
URL을 입력하면 단축 링크와 관리용 secret key를 바로 발급합니다. 알려진 위협이 없는 링크는 빠르게 이동하고 잠재적인 위협은 차단합니다.
```

Badge:

```text
srrrg.link
```

Create panel title:

```text
단축 링크 만들기
```

Create panel description:

```text
긴 URL을 붙여넣고 필요한 경우 만료 시각을 정하세요.
```

Help:

```text
secret key는 생성 직후 한 번만 확인할 수 있습니다.
```

### 1.6 API 문서 위치

API 링크는 다음처럼 낮은 우선순위로 둔다.

```text
개발자용 API가 필요하신가요? API 문서 보기 · OpenAPI JSON
```

시각:

- `font-size: 14px`
- 일반 텍스트는 muted
- 링크만 brand color
- primary button 형태 금지

## 2. 링크 생성 폼

### 2.1 입력 필드

Label:

```text
원본 URL
```

Placeholder:

```text
https://example.com/very/long/path?with=query
```

Validation:

| 상황 | 프론트 메시지 |
|---|---|
| 비어 있음 | `단축할 URL을 입력하세요.` |
| URL 형식 아님 | `http 또는 https로 시작하는 올바른 URL을 입력하세요.` |
| 2048자 초과 | `URL은 최대 2,048자까지 입력할 수 있습니다.` |
| 과거 만료 시각 | `만료 시각은 현재보다 이후여야 합니다.` |

프론트 validation은 사용자 경험용이다. 최종 정책은 API 응답을 따른다.

### 2.2 Submit Button

기본:

```text
단축 URL 만들기
```

Loading:

```text
생성 중...
```

Disabled 조건:

- 요청 진행 중
- URL 입력이 비어 있음
- 직접 입력 만료 시각이 invalid

### 2.3 생성 성공 후 포커스

권장:

- 생성 성공 시 result panel의 제목에 `tabindex="-1"`을 두고 focus 이동
- 스크린리더가 생성 결과를 읽을 수 있게 한다.
- 복사 버튼으로 바로 tab 이동 가능해야 한다.

## 3. 생성 결과 패널

### 3.1 정보 우선순위

1. 생성 성공 상태
2. 단축 URL
3. 단축 URL 복사
4. secret key
5. secret key 복사
6. secret key 보관 안내
7. 새 링크 만들기 흐름 유지

### 3.2 권장 구조

```html
<section class="result-panel" aria-labelledby="create-result-title">
  <p class="status-badge">생성 완료</p>
  <h2 id="create-result-title">단축 URL이 생성되었습니다</h2>

  <div class="result-field">
    <p class="result-label">단축 URL</p>
    <div class="copy-row">
      <a href="https://srrrg.link/aB3x9Q">https://srrrg.link/aB3x9Q</a>
      <button type="button">복사</button>
    </div>
  </div>

  <div class="result-field secret">
    <p class="result-label">Secret key</p>
    <div class="copy-row">
      <code>srrrg_sk_xxxxxxxxx</code>
      <button type="button">복사</button>
    </div>
    <p class="warning">이 secret key는 다시 확인할 수 없습니다.</p>
  </div>
</section>
```

### 3.3 Secret key 보관 안내

필수 문구:

```text
이 secret key는 다시 확인할 수 없습니다. 링크를 조회, 수정, 삭제하려면 지금 안전한 곳에 보관하세요.
```

이 문구는 작은 help text가 아니라 warning note로 보여준다.

시각:

- background: `#fffbeb`
- border: `#fde68a`
- text: `#92400e` 또는 `#b45309`
- radius: `12px`

## 4. 링크 관리 진입

### 4.1 홈 내 위치

생성 도구 아래에 낮은 강조의 관리 패널을 둔다.

```text
내 단축 링크 관리
단축 URL 또는 코드와 secret key를 입력하면 원본 URL, 만료 시각, 누적 통계를 확인할 수 있습니다.

[단축 URL 또는 코드]
[Secret key]
[조회하기]
```

관리 영역은 생성 폼과 같은 위계의 primary card로 만들지 않는다. 생성보다 약한 section surface가 적절하다.

### 4.2 입력 규칙

단축 URL 또는 코드:

- `aB3x9Q`
- `https://srrrg.link/aB3x9Q`

프론트에서 마지막 path segment를 추출한다.

Secret key:

- `srrrg_sk_`로 시작하는 값이 기대됨
- 화면에 다시 출력하지 않음
- localStorage/sessionStorage 저장 금지

### 4.3 조회 버튼 상태

| 상태 | 버튼 문구 |
|---|---|
| default | `조회하기` |
| loading | `조회 중...` |
| error | default로 복귀 |
| success | default로 복귀 후 관리 패널 표시 |

## 5. 링크 관리 상세

조회 성공 후 표시할 정보:

```text
단축 URL
원본 URL
만료 상태
생성 시각
최근 수정
진입 수
실제 이동 수
```

권장 구조:

```text
내 단축 링크 관리

상태: 사용 가능 / 만료됨 / 삭제됨

단축 URL                     [복사]
https://srrrg.link/aB3x9Q

원본 URL
[https://example.com/...                      ]

만료
[없음] [직접 입력...]

통계
진입 수 120       실제 이동 수 93

[변경 저장] [링크 삭제]
```

상태 badge:

| 링크 상태 | Badge | 색 |
|---|---|---|
| 사용 가능 | `사용 가능` | success |
| 만료됨 | `만료됨` | warning |
| 삭제됨 | `삭제됨` | danger |

만료된 링크는 관리 가능하므로 danger보다 warning으로 처리한다.

통계 설명:

```text
진입 수는 단축 URL에 접근한 횟수이고, 실제 이동 수는 원본 URL로 이동한 횟수입니다.
```

신뢰되지 않은 링크는 확인 화면에서 사용자가 이동하지 않을 수 있으므로 두 값이 다를 수 있다.

삭제 확인 문구:

```text
이 링크를 삭제할까요?
삭제 후에는 이 단축 URL로 이동할 수 없습니다.
```

확인 버튼:

```text
삭제하기
```

취소 버튼:

```text
취소
```

삭제는 되돌릴 수 없다는 톤을 유지하되, 과도한 공포 문구를 쓰지 않는다.

## 6. 리다이렉트 판정

### 6.1 정상 링크

유효한 안전 판정이 있으면 별도 확인 화면 없이 원본 URL로 즉시 `302` 응답한다.

### 6.2 잠재적 위협

이동 버튼 없이 `403` 차단 화면을 제공한다. 잠재적 위험이라는 제한적 표현, Google 출처, 판정의 오탐·미탐 가능성을 함께 안내한다.

### 6.3 검증 불가

검사 서비스 장애 등으로 안전 여부를 확인하지 못하면 `503`을 반환한다. 원본 URL 이동은 제공하지 않고 현재 단축 URL을 다시 요청하는 재시도 동작만 제공한다.

## 7. 리다이렉트 오류 화면

목적:

- 없는 링크, 만료된 링크, 삭제된 링크를 차분하게 설명한다.
- 사용자가 불필요하게 재시도하지 않게 한다.
- 홈으로 돌아가 새 링크를 만들 수 있게 한다.

상태별 권장 문구:

| 상태 | 제목 | 설명 |
|---|---|---|
| 404 | `링크를 찾을 수 없습니다` | `주소가 잘못되었거나 존재하지 않는 단축 링크입니다.` |
| 410 만료 | `만료된 링크입니다` | `이 단축 링크는 설정된 만료 시각이 지나 더 이상 이동할 수 없습니다.` |
| 410 삭제 | `삭제된 링크입니다` | `이 단축 링크는 생성자가 삭제해 더 이상 이동할 수 없습니다.` |
| 403 | `잠재적으로 위험한 링크입니다` | `Google Safe Browsing의 알려진 위협으로 분류되어 이동을 차단했습니다.` |
| 503 | `링크를 확인할 수 없습니다` | `URL 안전 검사를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.` |

Action:

```text
새 단축 링크 만들기
```

시각:

- 오류 badge는 danger 색 사용
- 전체 배경은 기존처럼 살짝 붉은 radial gradient 가능
- 버튼은 danger가 아니라 brand primary 또는 secondary를 사용한다.

## 8. 반응형 기준

Breakpoints:

| Breakpoint | 기준 |
|---|---|
| Desktop | `861px` 이상 |
| Tablet | `521px` 이상 `860px` 이하 |
| Mobile | `381px` 이상 `520px` 이하 |
| Small Mobile | `380px` 이하 |

Desktop:

- 홈은 2열 layout.
- 생성 폼 컬럼이 가치 제안 컬럼보다 약간 넓다.
- 결과 패널은 생성 카드 내부 하단 또는 바로 아래에 표시.
- 기능 카드 3개는 한 줄 배치.
- 관리 영역은 2열 입력 가능.

Tablet:

- 홈은 1열.
- 생성 폼이 히어로 설명보다 먼저 보여도 된다.
- 기능 카드는 1열 또는 3열 중 내용 밀도에 따라 선택.
- 관리 입력은 2열 유지 가능하지만 폭이 부족하면 1열.

Mobile:

- 모든 주요 버튼은 `width: 100%`.
- 만료 선택지는 2~3열로 wrap.
- URL display는 줄바꿈 필수.
- header는 세로 정렬.
- footer 문구는 360px 이하에서 두 줄 처리.
- 모달은 viewport 좌우 `14px` padding.

높이 처리:

- 생성 카드에는 고정 height를 사용하지 않는다.
- 필요한 영역만 `max-height`와 `overflow-y: auto`를 사용한다.
- confirm/error 페이지는 내용이 늘면 자연스럽게 커지도록 `min-height` 중심으로 바꾸는 것을 권장한다.
