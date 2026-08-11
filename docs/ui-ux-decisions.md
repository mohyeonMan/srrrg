# UI/UX 결정 기록

UI/UX 개선 과정에서 선택지가 생겼을 때 무엇을 왜 골랐는지 누적 기록한다.
새 결정은 아래에 계속 덧붙인다.

## 1. 캠페인 탭 키보드 이동 (재수색 #2)

**문제와 맥락**
`campaigns.html` 의 캠페인 상세 탭 4개는 `role="tablist"` / `role="tab"` 을 쓰지만 방향키 핸들러와
roving tabindex 가 없었다. 탭 4개가 모두 Tab 순서에 남고 좌우 방향키는 동작하지 않아
WAI-ARIA Authoring Practices 의 Tabs 패턴을 벗어난다.
같은 `console-tab` 클래스를 쓰는 `management.html` 탭은 이미 올바르게 구현돼 있었다.

**채택안**
`srrrg-management.js` 의 탭 처리 방식을 그대로 옮긴다. 선택된 탭만 `tabIndex = 0` 이고 나머지는 `-1`,
`ArrowLeft` / `ArrowRight` 로 이동하며 이동 시 포커스도 함께 옮긴다. 탭 4개라 순환 인덱스로 일반화했다.

**선택 근거**
정답 구현이 이미 저장소 안에 있어 새 패턴을 만들 이유가 없다. 동일 컴포넌트가 화면에 따라
다르게 동작하던 불일치도 함께 사라진다.

**제외한 대안**
- 라이브러리 도입: 새 의존성 금지 원칙에 어긋나고 8줄로 해결되는 문제다.
- `Home` / `End` 키까지 추가: APG 권장이지만 `management.js` 에 없어 두 화면이 다시 갈라진다.
  두 화면을 함께 올릴 때 별도로 다루는 편이 낫다.

**영향 파일** `static/js/srrrg-campaigns.js`

**검증** 데스크톱에서 `개요 → 링크 목록 → 링크 생성 → 설정` 을 방향키로 순회, `aria-selected` 와
`tabIndex` 가 함께 갱신되고 순환도 동작함을 확인.

## 2. 제출 피드백과 중복 제출 방지 (재수색 #4)

**문제와 맥락**
submit 핸들러 13개 중 9개가 처리 중 표시 없이 즉시 요청만 보냈다. 특히 프로젝트 생성과
템플릿 생성은 버튼 비활성화도 없어 더블클릭하면 실제로 2개가 만들어졌다.

**채택안**
`SrrrgCommon.submitting(button, pendingLabel, run)` 헬퍼를 추가한다. 버튼을 비활성화하고 라벨을
처리 중 문구로 바꾼 뒤 `finally` 에서 되돌리며, `dataset.pending` 으로 재진입을 막는다.
대상 핸들러는 `event.submitter` 를 넘겨 호출하고 기존 `form-message` 에 진행 문구를 함께 넣는다.

**선택 근거**
이미 `srrrg-common.js` 가 공용 모듈로 존재해 새 파일이나 의존성이 필요 없다. 버튼 잠금과
라벨 복구가 한곳에 모여 화면마다 다르게 구현될 여지가 없다. `finally` 복구라 실패 경로에서도
버튼이 잠긴 채 남지 않는다.

**제외한 대안**
- 각 핸들러에서 개별 처리: 기존에 로딩 표시가 있던 4곳이 이미 서로 다른 방식이었고,
  9곳을 각각 쓰면 불일치가 더 커진다.
- 전역 폼 submit 가로채기: 검증 실패로 요청을 보내지 않는 경우까지 잠기고, 어떤 버튼이
  제출 주체인지 판별이 불안정하다.
- 서버측 멱등키: 이번 UI/UX 범위를 벗어난다. CSV 업로드에만 이미 존재한다.

**영향 파일** `static/js/srrrg-common.js`, `srrrg-campaigns.js`, `srrrg-projects.js`,
`srrrg-project-settings.js`, `srrrg-project-utm-templates.js`

**검증** 프로젝트 생성 버튼을 연속 클릭해도 POST 가 1회만 나가고, 처리 중 라벨이 바뀌고
완료 후 원래 라벨로 복구됨을 확인.

## 3. 확인 대화상자를 네이티브 confirm 에서 <dialog> 로 (재수색 #5)

**문제와 맥락**
삭제·반납 등 되돌리기 어려운 동작 7건이 브라우저 `confirm()` 을 썼다. 디자인 시스템 밖이고,
브라우저가 "추가 대화상자 표시 안 함" 으로 억제할 수 있으며, 문구에 `410 Gone` 같은
HTTP 용어가 그대로 노출됐다.

**채택안**
`projects.html` 에 공용 `<dialog id="confirm-dialog">` 하나를 두고 `SrrrgCommon.confirmAction()` 이
제목·본문·버튼 라벨을 채워 `showModal()` 로 띄운다. Promise 로 결과를 돌려준다.
기본 포커스는 취소에 두고, 실행 버튼은 위험도에 따라 `danger-button` / `primary-button` 을 쓴다.
개발자 용어는 사용자 관점 문구로 바꿨다. 예) `410 Gone 을 반환합니다`
→ `목적지가 사라져 이동하지 않습니다`.

**선택 근거**
이미 `create-project-dialog` 가 네이티브 `<dialog>` + `showModal()` 을 쓰고 있어 포커스 트랩과
Esc 닫기를 공짜로 얻는다. `.project-create-dialog` / `.project-dialog-heading` 스타일도 재사용된다.
대화상자 마크업을 하나만 두어 7곳이 같은 모양을 갖는다.

**제외한 대안**
- 화면마다 전용 대화상자: 마크업 7배, 스타일 불일치 위험.
- 커스텀 오버레이 div: 포커스 트랩과 Esc 처리를 직접 구현해야 하고 접근성이 후퇴한다.
- confirm 유지하고 문구만 수정: 억제 가능성과 스타일 불일치가 남는다.

**트레이드오프**
대화상자가 없는 화면에서 `confirmAction()` 은 `true` 를 반환해 동작을 막지 않는다.
현재 confirm 사용처는 모두 `projects.html` 안에 임베드되므로 문제가 없지만, 조각을 다른
페이지에서 쓰게 되면 그 페이지에도 대화상자를 넣어야 한다. 그 전제를 주석으로 남겼다.

**영향 파일** `templates/projects.html`, `static/js/srrrg-common.js`, `srrrg-campaigns.js`,
`srrrg-project-settings.js`, `srrrg-project-utm-templates.js`, `static/css/srrrg/projects.css`

**구현 중 바로잡은 두 가지**
- 처음에는 `dialog` 의 `close` 이벤트로 결과를 확정했는데, 검증 환경에서 이 이벤트가 아예
  발생하지 않아 실행 버튼을 눌러도 Promise 가 영원히 대기했다. `dlg.close('accept')` 를
  직접 호출해도 이벤트가 없었다. 이벤트 의존을 버리고 열려 있는 대화상자의 resolver 를
  모듈 변수로 들고 있다가 버튼 클릭과 Esc 키에서 직접 확정하도록 바꿨다.
- 삭제 핸들러에서 `event.currentTarget` 을 `await confirmAction()` 이후에 사용했다.
  `currentTarget` 은 이벤트 전파가 끝나면 null 이 되므로 버튼을 미리 지역 변수로 잡아야 한다.

**영향 파일** `templates/projects.html`, `static/js/srrrg-common.js`, `srrrg-campaigns.js`,
`srrrg-project-settings.js`, `srrrg-project-utm-templates.js`, `static/css/srrrg/projects.css`

**검증** 수락 `true`, 취소 `false`, Esc `false` 로 각각 확정됨을 확인.
임시 캠페인을 만들어 실제 삭제까지 수행 → 대화상자 → 수락 → DELETE → 프로젝트로 복귀 →
목록에서 사라짐. 필드 삭제 대화상자도 확인. 네이티브 `confirm(` 잔존 0건, 콘솔 오류 0건.

## 4. CTA 위계 통일 (재수색 #6)

**문제와 맥락**
같은 레일에서 진입하는 형제 화면인데 `프로젝트 만들기` / `단축 URL 만들기` 는 primary,
`캠페인 만들기` 는 secondary 였다. `project-utm-templates`, `project-settings`,
`project-members` 는 primary 버튼이 아예 0개여서 각 화면의 주 동작이 드러나지 않았다.

**채택안**
"화면 또는 카드마다 주 동작 하나를 primary 로 둔다" 는 규칙을 적용했다.
캠페인 만들기, 템플릿 만들기, 필드 추가, 초대 보내기, 캠페인 정보 저장, 기본 목적지 저장,
CSV 업로드, 이름 저장, 선점하기, 프로젝트로 편입을 primary 로 올렸다.
보조 동작(더 보기, CSV 다운로드, 새 템플릿 열기)과 파괴적 동작(삭제)은 그대로 두었다.

**선택 근거**
핵심 사용자인 캠페인 매니저의 주 업무가 가장 약하게 보이던 상태를 바로잡는다.
카드 단위로 주 동작을 하나씩 두면 설정 화면처럼 카드가 여러 개인 경우에도 규칙이 일관된다.
기존 토큰과 클래스만 쓰고 새 변형을 만들지 않았다.

**제외한 대안**
- 화면당 primary 를 딱 1개로 제한: 설정 화면은 독립된 카드가 4개라 어느 하나만 강조하면
  나머지 저장 동작이 부차적으로 보인다.
- primary 를 그대로 두고 secondary 를 약화: 대비만 커지고 무엇이 주 동작인지는 여전히 모른다.

**영향 파일** `templates/projects.html`, `campaigns.html`, `project-utm-templates.html`,
`project-settings.html`, `project-members.html`

**검증** 화면별 primary 개수 확인. projects 3, campaigns 4, utm-templates 2, settings 3, members 1.

## 5. 태블릿 아이콘 레일 회귀 수정 (재수색 #3)

**문제와 맥락**
직전 반응형 작업에서 태블릿(768~1023px) 레일을 64px 아이콘 스트립으로 바꾸면서 두 가지
회귀가 생겼다. 아이콘만 남아 마우스 사용자가 뜻을 알 수 없었고, 필터·정렬 버튼을
`display: none` 으로 숨겨 콘텐츠를 걸러낼 방법이 사라졌다.

**채택안**
레일 폭을 64px → 88px 로 넓혀 필터·정렬 버튼을 세로로 배치해 되살린다.
모든 레일 항목에 `title` 을 달아 아이콘만 보이는 상태에서도 뜻을 알 수 있게 한다.
정적 항목 4개는 템플릿에, 동적 콘텐츠 항목은 `renderProjectItems()` 에서 설정한다.

**선택 근거**
88px 는 998px 기준 본문을 약 860px 확보한다. 64px 대비 24px 만 내주고 기능을 되찾는 편이,
본문 폭을 위해 기능을 없애는 것보다 낫다. `title` 은 새 컴포넌트 없이 마우스 힌트를 준다.

**제외한 대안**
- 필터·정렬을 아이콘 버튼으로 축약: 라벨이 `전체 / 캠페인만 / 단일링크만` 처럼 상태에 따라
  바뀌는 순환 버튼이라 아이콘으로 현재 상태를 표현하기 어렵다.
- 태블릿도 드로어로 전환: 프로젝트 내부 맥락을 항상 보여야 한다는 요구와 어긋난다.
- 커스텀 tooltip 컴포넌트: 네이티브 `title` 로 충분하고 새 코드가 필요 없다.

**남은 확인 사항**
아이콘만 보이는 상태에서 접근명은 `.sr-only` 방식(1×1, `clip`)으로 DOM 에 남아 있다.
브라우저 확장의 접근성 트리가 이 링크들을 이름 없음으로 보고한 적이 있어, 실제 스크린리더로
한 번 더 확인하는 편이 좋다. `title` 추가로 접근명 경로는 하나 더 확보됐다.

**영향 파일** `static/css/srrrg/projects.css`, `templates/projects.html`,
`static/js/srrrg-projects.js`

**검증**
브라우저 창 리사이즈가 세션 중간부터 뷰포트에 반영되지 않아, 같은 출처 iframe 을 실제 폭으로
띄워 측정했다. iframe 안에서는 미디어쿼리가 iframe 뷰포트 기준으로 평가되므로 실제 렌더 결과다.

| 폭 | 티어 | 레일 | 본문 | 필터·정렬 | 드로어 트리거 | 가로 오버플로 |
|---|---|---|---|---|---|---|
| 1108px | desktop | 290px | 750px | 노출 | 숨김 | 0 |
| 900px | tablet | 88px | 743px | 노출 | 숨김 | 0 |
| 400px | mobile | 300px 드로어 | 363px | 노출 | 노출 | 0 |

레일 항목 `title` 은 정적 4개와 동적 콘텐츠 항목 모두에서 확인
(`단축링크 생성`, `캠페인 생성`, `UTM 템플릿 관리`, `프로젝트 설정`, `캠페인 · 링쥐캠페인`).

## 6. 프로젝트 0개 빈 상태 (재수색 #1)

**문제와 맥락**
`loadProjects()` 는 프로젝트가 하나라도 있으면 첫 항목을 자동 선택하므로 이 패널은
0개일 때만 보인다. 그런데 문구가 `프로젝트를 선택하세요` 였고 생성 버튼이 없어,
가입 직후 사용자에게 선택할 것도 만들 방법도 없는 화면이 됐다. 유일한 출구는
제목 옆 라벨 없는 `+` 글리프였다.

**채택안**
문구를 `아직 프로젝트가 없습니다` 로 바꾸고 프로젝트가 무엇인지 한 문장으로 설명한다.
기존 `create-project-dialog` 를 그대로 여는 `첫 프로젝트 만들기` primary CTA 를 추가한다.
`+` 버튼과 `▾` 프로젝트 선택 글리프에 `title` 을 달아 마우스에서도 뜻이 드러나게 한다.

**선택 근거**
빈 상태는 "왜 비었는지 + 다음 행동" 을 줘야 한다. 새 다이얼로그를 만들지 않고 기존 것을
재사용하면 검증·포커스 처리가 이미 갖춰진 경로를 그대로 쓴다. `+` 는 이미
`aria-label` 이 있어 스크린리더는 문제없었고 마우스 힌트만 부족했다.

**제외한 대안**
- 프로젝트 0개면 다이얼로그를 자동으로 띄우기: 맥락 설명을 읽기 전에 입력을 요구하고,
  Esc 로 닫으면 다시 빈 화면이라 같은 문제로 돌아온다.
- 온보딩 단계에 프로젝트 생성 추가: `onboarding.html` 은 이름·이메일 확인 용도이며
  이번 범위를 넘어선다.
- `+` 를 텍스트 버튼으로 교체: 제목 줄 레이아웃을 다시 잡아야 하고 이번 목표를 넘어선다.

**영향 파일** `templates/projects.html`, `static/js/srrrg-projects.js`,
`static/css/srrrg/projects.css`

**검증** 빈 상태 패널을 노출시켜 CTA 클릭 시 `create-project-dialog` 가 열리고 이름 입력에
포커스가 가는 것을 확인. `+` 버튼과 동일 경로를 공유.

**참고**
빈 상태의 `GET STARTED` 영문 kicker 는 재수색 #12(영문 kicker 전반)에 속해 이번 범위에서
제외했다. 문구를 손대면서도 해당 요소는 그대로 두었다.

## 이번 범위에서 의도적으로 제외한 항목

재수색 목록 중 #7~#17 은 요청 범위 밖이라 손대지 않았다.
`management.html` 의 "이 작업은 되돌릴 수 없습니다"(#7), 44px 미만 탭 타깃(#8),
통계 필터 정렬(#9), `/projects` 무파라미터 상태 불일치(#10), CTA 없는 나머지 빈 상태(#11),
영문 kicker(#12), 원시 enum 노출(#13) 등이 남아 있다.
