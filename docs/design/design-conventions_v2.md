# srrrg 디자인 컨벤션 v2

이 문서는 srrrg UIUX v2 문서의 진입점이다.

기존 단일 문서가 너무 길어져서, 역할별로 아래 문서로 분리한다. 개발자는 구현하려는 작업 범위에 맞는 문서만 열어도 된다.

## 문서 맵

| 문서 | 목적 | 읽는 사람 |
|---|---|---|
| [01_product_strategy.md](./v2/01_product_strategy.md) | 제품 방향, 사용자, 핵심 UX 변경 방향, 정보 구조 | CEO, 기획자, 디자이너, 프론트 구현자 |
| [02_visual_system.md](./v2/02_visual_system.md) | 색상, 타이포그래피, 레이아웃, 버튼, 입력, 결과 패널 등 공통 컴포넌트 | 디자이너, 프론트 구현자 |
| [03_screen_specs.md](./v2/03_screen_specs.md) | 홈, 생성, 관리, 링크 확인, 오류 화면의 상세 명세 | 기획자, 프론트 구현자, QA |
| [04_implementation_qa.md](./v2/04_implementation_qa.md) | CSS 분리, JS 상태, API 연결, 접근성, QA, 구현 순서 | 프론트 구현자, 백엔드 구현자, QA |

## v2 핵심 방향

- 첫 화면의 주인공은 `API 문서 보기`가 아니라 `단축 URL 만들기`다.
- 생성 결과는 모달보다 인라인 결과 패널을 우선한다.
- 만료 설정은 작은 select가 아니라 빠른 선택 버튼으로 노출한다.
- 링크 관리는 생성 흐름보다 한 단계 낮은 보조 흐름으로 둔다.
- `신뢰할 수 있는 링크인가요?` 컨셉은 유지하되 원본 도메인 강조와 `돌아가기` 선택지를 추가한다.
- 푸른색 메인 컬러, 밝은 SaaS 도구형 인상, 친절한 보안 문구는 유지한다.

## 구현 순서 요약

1. 홈 첫 화면에서 URL 생성 폼의 위계를 올린다.
2. 생성 결과 모달을 인라인 결과 패널로 바꾼다.
3. 만료 select를 segmented quick options로 바꾼다.
4. 관리 진입과 링크 확인/오류 화면을 정리한다.
5. focus 이동, status message, modal focus trap 등 접근성을 보강한다.

상세 구현 기준은 [04_implementation_qa.md](./v2/04_implementation_qa.md)를 따른다.
