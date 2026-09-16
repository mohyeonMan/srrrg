# 기능과 역할 패키지 구조

## 목적

패키지 경로만 보고 코드가 담당하는 업무 기능과 기술적 역할을 함께 알 수 있게 한다.
업무 기능을 먼저 나누고, 각 기능 안에서 HTTP 계약, 유스케이스, 저장, 상태와 외부 통신을 구분한다.

## 구조 원칙

~~~text
업무 도메인 / 세부 기능 / 역할 / 타입
project / invitation / service / ProjectInvitationService
campaign / link / repository / CampaignLinkBatchRepository
link / redirect / controller / RedirectController
~~~

- `controller`는 HTTP 계약과 인증 주체 변환을 담당한다.
- `service`는 유스케이스, 권한과 트랜잭션 흐름을 담당한다.
- `repository`는 저장과 조회 의미를 담당한다.
- `model`은 entity, 값 객체와 기능에 속한 상태를 담당한다.
- `dto`는 기능 경계를 오가는 요청과 응답을 담당한다.
- `client`와 `config`는 외부 시스템 연결과 프레임워크 구성을 명시할 때 사용한다.
- 실제 책임이 없는 빈 역할 패키지나 단순 위임 계층은 만들지 않는다.

## 현재 기능 지도

~~~text
auth/{login,session,request}/{controller,handler,service,repository,model,adapter,client,config,filter}
identity/{account,connection}/{controller,service,repository,model}
project/{controller,service,repository,model}
project/{membership,invitation,apikey,subdomain}/{controller,service,repository,model,client}
utmtemplate/{controller,service,repository,model}
campaign/{controller,service,repository,model,dto}
campaign/utm/{controller,service,repository,model}
campaign/link/{controller,service,model,dto}
campaign/link/{batch,csv}/{controller,service,repository,model}
link/{model,repository}
link/{address,creation,anonymous,management,destination,redirect,access,risk}/{controller,service,repository,model,dto,client,config,handler}
statistics/{controller,service,repository,model,dto}
web/{page,documentation,error}/{controller,service,config,handler,model,dto}
common/{ratelimit,metrics,util}
~~~

역할 목록은 허용 가능한 경로를 보여 주며 모든 기능에 각 역할이 반드시 존재해야 한다는 뜻은 아니다.

## 소유권 결정

- UTM 템플릿 정의는 프로젝트 범위에서 독립적인 생명주기를 가지므로 `utmtemplate`이 소유한다.
- 캠페인의 템플릿 선택과 기본값은 `campaign.utm`이 소유한다.
- 캠페인 링크 발행, 조회, JSON batch와 CSV 흐름은 `campaign.link`이 소유한다.
- 링크 모델과 공통 저장은 `link`가 소유하고 캠페인 기능은 링크 생성 기능을 사용한다.
- API 키의 발급·폐기와 유효성은 `project.apikey`가 소유하고 HTTP 인증 적용은 `auth.request`가 담당한다.
- OAuth 신원과 연결은 `identity.connection`이 소유하고 로그인 완료 순서는 `auth.login`이 조율한다.
- 프로젝트 서브도메인 설정은 `project.subdomain`, 주소 생성과 요청 호스트 해석은 링크 기능이 담당한다.
- 접속 이벤트 수집은 `link.access`, 집계와 보고는 `statistics`가 담당한다.

## CSV 처리 방식

`campaign.link.csv`는 업로드를 요청 트랜잭션 안에서 끝낸다. 업로드당 10,000행 제한을 전제로 파일 전체를
먼저 검증하고 문제가 없을 때만 링크를 모두 생성하므로, 작업 상태 테이블도 worker도 lease도 없다.
따라서 이 패키지에는 `controller`, `service`만 있고 `model`·`repository`는 두지 않는다.

이 선택은 상한을 전제로 성립한다. 한 번에 10,000행을 훨씬 넘겨야 하면 요청 타임아웃과 트랜잭션 유지
시간이 먼저 문제가 되므로, 그때는 다시 비동기 작업 구조가 필요하다. 흐름은
[docs/flow/09-campaign-import.md](../flow/09-campaign-import.md)를 따른다.
