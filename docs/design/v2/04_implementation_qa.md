# srrrg UIUX v2 - 구현 및 QA

## 1. CSS 구조

현재는 템플릿마다 인라인 style이 중복된다. v2 개편 시에는 공통 CSS 파일 분리를 권장한다.

권장 파일:

```text
src/main/resources/static/css/srrrg.css
```

템플릿:

```html
<link rel="stylesheet" th:href="@{/css/srrrg.css}">
```

공통화 우선순위:

1. color, radius, shadow, spacing token
2. header/footer
3. button
4. input
5. surface/card
6. URL display
7. status message
8. modal

CSS 변수 예시:

```css
:root {
  --color-brand: #2563eb;
  --color-brand-strong: #1d4ed8;
  --color-brand-deep: #4f46e5;
  --color-brand-soft: #eff6ff;
  --color-brand-soft-hover: #dbeafe;
  --color-brand-border: #bfdbfe;
  --color-text: #0f172a;
  --color-text-muted: #475569;
  --color-text-subtle: #64748b;
  --color-surface: #ffffff;
  --color-surface-glass: rgba(255, 255, 255, 0.92);
  --color-surface-muted: #f8fafc;
  --color-border: #e2e8f0;
  --color-border-strong: #dbe4ef;
  --radius-lg: 14px;
  --radius-xl: 18px;
  --radius-2xl: 20px;
  --shadow-panel: 0 18px 50px rgba(15, 23, 42, 0.08);
  --shadow-action: 0 12px 24px rgba(37, 99, 235, 0.22);
}
```

## 2. HTML 구조 변경 우선순위

1. 홈 생성 카드의 위계를 올린다.
2. API 문서 링크를 secondary text link로 내린다.
3. 생성 결과 모달을 인라인 result panel로 바꾼다.
4. 만료 select를 segmented choice로 바꾼다.
5. 관리 탭을 별도 관리 섹션으로 바꾸거나 시각 위계를 낮춘다.
6. confirm 화면에 `돌아가기` 액션과 원본 도메인 강조를 추가한다.
7. 오류 화면에 홈으로 돌아가는 action을 추가한다.

## 3. JavaScript 상태 관리

현재 vanilla JS를 유지해도 충분하다. 다만 상태 이름은 명확히 나눈다.

권장 상태:

```text
createFormState
- originalUrl
- expiresAt
- expiresMode
- loading
- result
- error

managementState
- code
- secretKey
- loading
- currentLink
- message
- error
```

DOM 조작 규칙:

- 텍스트 변경 함수와 visibility 변경 함수를 분리한다.
- API response rendering 함수는 상태 업데이트 후 한 번만 호출한다.
- copy button의 `복사됨` 상태는 timer cleanup을 고려한다.
- secret key는 상태에 오래 남기지 않는 편이 좋지만, 결과 패널 표시 중에는 복사를 위해 메모리 상태에 둘 수 있다.
- secret key를 storage에 저장하지 않는다.

## 4. API 연결 기준

생성:

- `POST /api/links`
- `originalUrl`
- `expiresAt`
- 성공 `201 Created`
- 응답 `shortUrl`, `secretKey`

관리:

- `GET /api/links/{code}`
- `PATCH /api/links/{code}`
- `DELETE /api/links/{code}`
- secret key는 `X-Srrrg-Secret-Key` header

오류:

- `ApiErrorResponse.message`를 사용자 메시지로 표시한다.
- message가 없으면 fallback 문구를 사용한다.

## 5. 접근성 명세

### 5.1 기본

- 모든 페이지는 `lang="ko"`를 유지한다.
- 모든 페이지는 viewport meta를 둔다.
- interactive element는 keyboard로 접근 가능해야 한다.
- focus-visible 스타일은 제거하지 않는다.
- 색상만으로 상태를 전달하지 않는다.

### 5.2 Form

- input에는 visible label이 있어야 한다.
- placeholder는 label 대체 금지.
- 오류 메시지는 `aria-describedby`로 연결한다.
- submit 중에는 버튼 disabled와 status message를 함께 제공한다.
- 결과 생성 후에는 결과 제목으로 focus를 이동할 수 있다.

### 5.3 Tabs

현재 탭 구조를 유지할 경우:

- `role="tablist"`
- `role="tab"`
- `role="tabpanel"`
- `aria-selected`
- `aria-controls`
- 방향키 이동 지원 권장

다만 v2 홈에서는 생성/관리 탭보다 생성 폼 + 관리 섹션 구조를 권장한다.

### 5.4 Modal

모달을 사용하는 경우:

- `role="dialog"`
- `aria-modal="true"`
- `aria-labelledby`
- 열릴 때 첫 focus 이동
- 닫힐 때 trigger로 focus 복귀
- Escape 닫기
- overlay 클릭 닫기
- body scroll lock
- focus trap

생성 성공 결과에는 모달을 쓰지 않는 것을 권장한다.

### 5.5 Dynamic Message

동적 메시지는 아래 구조를 사용한다.

```html
<p role="status" aria-live="polite"></p>
```

치명적 오류나 즉시 알아야 하는 오류가 아니라면 `assertive`는 사용하지 않는다.

## 6. QA 체크리스트

### 6.1 홈

- 첫 화면에서 URL input과 `단축 URL 만들기` 버튼이 보인다.
- API 문서 링크가 primary action처럼 보이지 않는다.
- URL을 입력하지 않으면 생성 요청을 보내지 않는다.
- http/https URL 입력이 가능하다.
- 만료 없음으로 생성하면 `expiresAt: null`이 전송된다.
- 빠른 만료 선택은 미래 시각으로 계산된다.
- 직접 입력 만료는 사용자가 수정할 수 있다.
- 생성 성공 후 단축 URL과 secret key가 보인다.
- 복사 버튼이 동작한다.
- 복사 후 버튼 문구가 잠시 `복사됨`으로 바뀐다.
- secret key 일회성 안내가 결과 영역에 명확히 보인다.

### 6.2 관리

- code만 입력해도 조회된다.
- 전체 단축 URL을 입력해도 code를 추출한다.
- secret key 없이 조회할 수 없다.
- 조회 성공 후 원본 URL, 만료 시각, 통계가 보인다.
- 변경 없이 저장하면 `변경된 값이 없습니다.`가 보인다.
- 원본 URL 변경 후 저장하면 화면이 갱신된다.
- 만료 시각을 비우면 무기한으로 바뀐다.
- 삭제 전 확인을 거친다.
- 삭제 후 관리 상태가 초기화된다.

### 6.3 확인 화면

- 제목은 `신뢰할 수 있는 링크인가요?`다.
- 원본 도메인이 눈에 띄게 표시된다.
- 전체 URL을 확인할 수 있다.
- `원본 URL로 이동` 버튼으로 이동한다.
- `돌아가기` 또는 홈 이동 액션이 있다.
- 모바일에서 URL과 버튼이 겹치지 않는다.

### 6.4 오류 화면

- 404와 410의 메시지가 구분된다.
- 요청한 단축 URL이 표시된다.
- 홈으로 돌아가는 액션이 있다.
- 오류 화면이 과도하게 위협적으로 보이지 않는다.

### 6.5 접근성

- 키보드만으로 모든 조작이 가능하다.
- focus-visible이 보인다.
- 모달을 쓴다면 focus trap이 동작한다.
- 동적 결과와 오류가 스크린리더에 전달된다.
- color contrast가 충분하다.
- label 없는 input이 없다.

### 6.6 반응형

- 1120px desktop에서 layout이 과하게 넓어지지 않는다.
- 860px 이하에서 1열로 자연스럽게 전환된다.
- 520px 이하에서 버튼은 full width다.
- 380px 이하에서 header/footer 문구가 깨지지 않는다.
- 긴 URL은 모든 viewport에서 컨테이너 밖으로 넘치지 않는다.

## 7. 구현 우선순위

### Phase 1: 홈 핵심 전환

목표:

- 첫 화면의 주인공을 URL 생성 폼으로 변경
- API 문서 링크 위계 낮추기
- 카드 radius와 shadow 정리

완료 기준:

- 첫 viewport에서 URL 입력과 생성 버튼이 보인다.
- primary CTA가 `단축 URL 만들기` 하나로 명확하다.
- 기존 기능 회귀 없이 생성 API 호출이 유지된다.

### Phase 2: 생성 결과 UX 개선

목표:

- 생성 결과 모달을 인라인 result panel로 변경
- secret key 보관 안내 강화
- 복사 UX 정리

완료 기준:

- 생성 성공 후 모달 없이 결과가 표시된다.
- 단축 URL과 secret key 복사가 가능하다.
- secret key 일회성 안내가 명확하다.

### Phase 3: 만료 선택 개선

목표:

- select를 segmented quick options로 변경
- 직접 입력 노출 조건 정리

완료 기준:

- `없음`, `1시간`, `하루`, `1개월`, `1년`, `직접 입력`이 명확히 보인다.
- API 전송 값은 기존과 동일하다.

### Phase 4: 관리와 확인 화면 정리

목표:

- 관리 진입 위계 조정
- confirm 화면의 도메인 강조와 돌아가기 액션 추가
- 오류 화면 action 추가

완료 기준:

- 관리 기능은 유지하되 홈 생성 경험을 방해하지 않는다.
- 확인 화면에서 이동하지 않는 선택이 명확하다.
- 오류 화면에서 다음 행동이 있다.

### Phase 5: 접근성 완성도

목표:

- focus 이동
- status message
- modal focus trap
- keyboard interaction

완료 기준:

- 키보드와 스크린리더 기준 QA 체크리스트를 통과한다.

## 8. 최종 방향 요약

v2의 핵심은 시각적으로 더 화려하게 만드는 것이 아니다.

핵심은 다음이다.

- 사용자는 더 빨리 URL을 입력한다.
- 생성 결과는 더 자연스럽게 이어진다.
- secret key의 중요성은 더 분명해진다.
- 만료 설정은 더 이해하기 쉬워진다.
- 낯선 링크 확인 화면은 더 믿을 수 있게 된다.
- API 문서는 필요한 사람에게만 조용히 제공된다.

srrrg는 작고 빠른 도구다. 그래서 v2 UI는 `멋있는 랜딩 페이지`보다 `바로 쓰고, 중요한 순간에 정확히 안내하는 제품`이어야 한다.
