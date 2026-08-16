# project — 멤버와 초대

프로젝트에 사람을 붙이는 경로. 초대는 **이메일로 보낸 토큰**을 수락하는 방식이고, 토큰은 원문 대신 해시만 DB에 남는다.

| Method | Path | 핸들러 | 최소 역할 |
|---|---|---|---|
| GET | `/api/web/projects/{projectId}/members` | `ProjectController.members` | VIEWER |
| PATCH | `/api/web/projects/{projectId}/members/{memberId}` | `ProjectController.changeRole` | OWNER |
| DELETE | `/api/web/projects/{projectId}/members/{memberId}` | `ProjectController.remove` | OWNER |
| GET | `/api/web/projects/{projectId}/invitations` | `ProjectController.invitations` | OWNER |
| POST | `/api/web/projects/{projectId}/invitations` | `ProjectController.invite` | OWNER |
| POST | `/api/web/invitations/{token}/accept` | `ProjectController.accept` | 로그인만 |
| DELETE | `/api/web/invitations/{invitationId}` | `ProjectController.cancel` | OWNER |
| POST | `/api/web/invitations/{invitationId}/resend` | `ProjectController.resend` | OWNER |
| GET | `/invitations/{token}` | `InvitationPageController.invitation` | 없음 |

`{memberId}`는 **멤버십 행의 ID가 아니라 대상 사용자의 userId**다. 복합키 `ProjectMember.id(projectId, userId)` 구조라서 그렇다.

## ProjectInvitation 엔티티

| 필드 | 의미 |
|---|---|
| `tokenHash` | SHA-256 64자. 원문은 메일에만 실린다 |
| `expiresAt` | 생성 시각 + 7일 |
| `cancelledAt` / `acceptedAt` | 둘 다 null이고 미만료여야 `isUsable()` |

**DB에 부분 unique 제약이 있다** — 같은 프로젝트·이메일 조합에서 활성 초대는 하나뿐이다. 아래 여러 곳에서 `DataIntegrityViolationException`을 잡는 이유.

---

### GET /api/web/projects/{projectId}/members

멤버 목록.

```
ProjectController.members(principal, projectId)

    ProjectService.projectMembers(userId, projectId)
        @Transactional(readOnly = true)
        requireRole(userId, projectId, VIEWER)

        ProjectMemberRepository.findByIdProjectId(projectId)
            멤버 전원. 뷰어도 누가 있는지는 볼 수 있다.
```

---

### PATCH /api/web/projects/{projectId}/members/{memberId}

역할 변경.

```
ProjectController.changeRole(principal, projectId, memberId, request)
    (Bean Validation) role: @NotNull
    → 204 No Content

    ProjectService.changeMemberRole(actorId, projectId, memberId, role)
        @Transactional
        requireRole(actorId, projectId, OWNER)

        ProjectMemberRepository.lockByProjectAndUser(projectId, memberId)
            비관적 잠금으로 대상 멤버십을 잡는다. 아래 "마지막 OWNER" 검사와
            실제 변경 사이에 다른 파드가 끼어들지 못하게 하기 위함.
            → 없으면 IllegalArgumentException (400)

        (마지막 OWNER 보호)
            대상이 OWNER이고, 새 역할이 OWNER가 아니고,
            ProjectMemberRepository.countByIdProjectIdAndRole(projectId, OWNER) == 1 이면 거부.
            → IllegalStateException (500)

        ProjectMember.changeRole(role)
```

**핵심 1가지**

- **행 잠금이 "마지막 OWNER" 불변식을 지킨다.** 잠금이 없으면 두 파드가 동시에 서로 다른 OWNER를 강등해 프로젝트에 OWNER가 하나도 남지 않을 수 있다. 애플리케이션 카운트 검사만으로는 막을 수 없는 종류의 경합이다.

---

### DELETE /api/web/projects/{projectId}/members/{memberId}

멤버 제거. 이 도메인에서 유일한 하드 삭제다.

```
ProjectController.remove(principal, projectId, memberId)
    → 204 No Content

    ProjectService.removeMember(actorId, projectId, memberId)
        @Transactional
        requireRole(actorId, projectId, OWNER)

        ProjectMemberRepository.lockByProjectAndUser(projectId, memberId)
            → 없으면 IllegalArgumentException (400)

        (마지막 OWNER 보호)
            countByIdProjectIdAndRole(projectId, OWNER) == 1 이고 대상이 OWNER면 거부.
            → IllegalStateException (500)

        ProjectMemberRepository.delete(member)
            멤버십 행을 지운다. 그 사람이 만든 링크는 남는다(Link.createdBy는 그대로).
```

**핵심 1가지**

- 자기 자신을 제거하는 것도 막지 않는다. 마지막 OWNER만 아니면 OWNER가 스스로 나갈 수 있다.

---

### GET /api/web/projects/{projectId}/invitations

대기 중인 초대 목록. **OWNER만** 볼 수 있다 — 초대 대상 이메일이 담기기 때문이다.

```
ProjectController.invitations(principal, projectId)

    ProjectService.projectInvitations(userId, projectId)
        @Transactional(readOnly = true)
        requireRole(userId, projectId, OWNER)

        ProjectInvitationRepository.findByProjectIdAndCancelledAtIsNullAndAcceptedAtIsNull(projectId)
            취소·수락된 것은 제외. 만료된 것은 걸러지지 않아 목록에 남는다.
```

---

### POST /api/web/projects/{projectId}/invitations

초대 발송.

```
ProjectController.invite(principal, projectId, request)
    (Bean Validation) email: @Email @NotBlank, role: @NotNull
    → 201 Created

    ProjectService.invite(userId, projectId, email, role)
        @Transactional
        requireRole(userId, projectId, OWNER)

        (역할 검사)
            OWNER로는 초대할 수 없다. 소유권 이전은 초대가 아니라 역할 변경으로 한다.
            → IllegalArgumentException (400)

        validEmail(email)
            정규식 + 320자 제한, 소문자화.
            → IllegalArgumentException (400)

        project(projectId)
            삭제된 프로젝트면 "찾을 수 없음"으로 처리한다.
            → IllegalArgumentException (400)

        UserRepository.findByEmail(email)
            ProjectMemberRepository.findByIdProjectIdAndIdUserId(projectId, user.getId())
                이미 멤버인 이메일이면 거부. users.email은 공급자 검증분뿐이므로
                이메일이 없는 계정으로 참여한 멤버는 이 검사에 안 걸린다 — 오탐 없는 힌트일 뿐이고,
                권위 있는 판정은 수락 시점의 alreadyMember다.
                → IllegalArgumentException (400)

        ProjectInvitationRepository.findByProjectIdAndEmailAndCancelledAtIsNullAndAcceptedAtIsNull(...)
            같은 이메일로 대기 중인 초대가 있으면
              - 아직 유효하면 거부 → IllegalArgumentException (400)
              - 만료됐으면 cancel() 해서 자리를 비운다 (부분 unique 제약 때문)

        SecureRandomStringGenerator.generate(TOKEN_CHARS, 43)
            43자 URL-safe 랜덤. 원문은 메일에만 나가고 DB에는 해시만 남는다.

        ProjectInvitationRepository.saveAndFlush(ProjectInvitation.create(..., sha256(raw), now + 7d))
            즉시 flush해 부분 unique 위반을 여기서 잡는다.
            → DataIntegrityViolationException → IllegalArgumentException (400)

        InvitationEmailSender.send(email, projectName, baseUrl + "/invitations/" + rawToken)
            JavaMailSender가 없거나 spring.mail.host / srrrg.mail.from 이 비어 있으면 예외.
            → IllegalStateException (500)
```

**핵심 2가지**

- **메일 발송이 트랜잭션 안에 있다.** SMTP가 실패하면 `IllegalStateException`이 나면서 초대 행까지 롤백된다. 발송 실패 시 유령 초대가 남지 않는다는 장점과, SMTP 지연이 DB 트랜잭션을 붙잡는다는 단점을 함께 가진다.
- **만료된 초대를 자동으로 정리한다.** 부분 unique 제약 때문에 만료된 초대가 남아 있으면 같은 사람을 다시 초대할 수 없다. 그래서 새 초대를 만들기 전에 `cancel()`로 비운다.

---

### POST /api/web/invitations/{invitationId}/resend

재발송. 기존 초대를 취소하고 새로 만든다.

```
ProjectController.resend(principal, invitationId)

    ProjectService.resend(userId, invitationId)
        @Transactional

        invitation(invitationId)
            ProjectInvitationRepository.findById → 없으면 IllegalArgumentException (400)

        requireRole(userId, old.getProject().getId(), OWNER)
            projectId를 경로가 아니라 초대에서 얻는다. 남의 프로젝트 초대 ID를 넣어도
            그 프로젝트의 OWNER가 아니면 여기서 막힌다.

        (수락 여부 검사)
            이미 수락된 초대는 재발송 대상이 아니다.
            → IllegalArgumentException (400)

        ProjectInvitation.cancel()
            기존 초대를 먼저 취소한다. 부분 unique 제약 때문에 필수.

        ProjectService.invite(userId, projectId, old.getEmail(), old.getRole())
            같은 이메일·역할로 새 초대를 만든다. 위 invite 흐름 전체를 다시 탄다.
            → 토큰이 새로 발급되므로 기존 메일의 링크는 죽는다.
```

**핵심 1가지**

- **자기 호출(self-invocation)이다.** `resend`가 같은 빈의 `invite`를 직접 부르므로 `@Transactional` 프록시를 거치지 않는다. 다만 두 메소드가 모두 `@Transactional`이고 기본 전파가 `REQUIRED`라, 이미 열려 있는 `resend`의 트랜잭션에 그대로 참여하는 것과 결과가 같다.

---

### DELETE /api/web/invitations/{invitationId}

초대 취소.

```
ProjectController.cancel(principal, invitationId)
    → 204 No Content

    ProjectService.cancel(userId, invitationId)
        @Transactional
        invitation(invitationId)
        requireRole(userId, invitation.getProject().getId(), OWNER)
        ProjectInvitation.cancel()
            cancelledAt만 찍는다. 행은 남는다.
```

---

### POST /api/web/invitations/{token}/accept

초대 수락. **로그인만 되어 있으면 누구나** 호출할 수 있다 — 토큰 자체가 인증이다.

```
ProjectController.accept(principal, token)

    ProjectService.accept(userId, rawToken)
        @Transactional

        ProjectInvitationRepository.findByTokenHash(sha256(rawToken))
            → 없으면 IllegalArgumentException (400)

        (프로젝트 삭제 검사)
            → IllegalStateException (500)

        ProjectInvitation.isUsable(now)
            취소·수락·만료 여부를 한 번에 본다.
            → IllegalStateException (500)

        ProjectMemberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
            이미 멤버라면 멤버십은 그대로 두고 초대만 소진시킨다.
            → AcceptedInvitation(projectId, alreadyMember = true)

        ProjectMemberRepository.save(new ProjectMember(project, user, invitation.getRole()))
            초대에 적힌 역할로 가입시킨다.

        ProjectInvitation.accept()
            acceptedAt을 찍어 재사용을 막는다.

    → AcceptInvitationResponse(projectId, alreadyMember)
```

**핵심 2가지**

- **초대 이메일과 로그인 계정이 일치하는지 확인하지 않는다.** 토큰을 가진 로그인 사용자면 누구든 수락할 수 있다. 토큰이 유일한 자격 증명이라는 설계다.
- **이미 멤버여도 초대를 소진시킨다.** 그러지 않으면 수락된 적 없는 초대가 영원히 남아 부분 unique 제약을 차지한다.

---

### GET /invitations/{token}

초대 수락 페이지. 로그인 없이 열 수 있다.

```
InvitationPageController.invitation(token, principal, model)
    보안 설정에서 /invitations/** 는 permitAll이라 미로그인 상태로도 열린다.

    ProjectService.invitationPreview(rawToken)
        @Transactional(readOnly = true)

        ProjectInvitationRepository.findByTokenHash(sha256(rawToken))
            프로젝트 삭제 여부와 사용가능 여부를 filter로 확인한다.
            무효하면 예외를 던지지 않고 available=false 를 돌려준다.
            초대의 존재 여부를 오류 코드로 흘리지 않기 위해 성공 응답에 담는다.

    → InvitationPreview(available, projectName, role, expiresAt)

    (모델 구성)
        authenticated = principal != null
        returnTo = "/invitations/" + token
            미로그인이면 화면이 로그인 링크를 이 returnTo와 함께 렌더한다.
            로그인 후 다시 이 페이지로 돌아와 수락 버튼을 누르는 흐름.

    → invitation.html
```

**핵심 1가지**

- 이 페이지는 **읽기만** 한다. 실제 수락은 화면의 버튼이 `POST /api/web/invitations/{token}/accept`를 부른다. GET으로 부수효과를 내지 않는 원칙을 지킨 것 — 메일 클라이언트의 링크 프리페치가 초대를 자동 수락시키지 않는다.
