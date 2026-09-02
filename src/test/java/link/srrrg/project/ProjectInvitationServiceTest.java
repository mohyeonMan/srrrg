package link.srrrg.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;

class ProjectInvitationServiceTest {
	private final ProjectInvitationRepository invitations = mock(ProjectInvitationRepository.class);
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final ProjectRepository projects = mock(ProjectRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final ProjectInvitationService service = new ProjectInvitationService(
			invitations, members, projects, users, new ProjectAccessService(members),
			mock(SecureRandomStringGenerator.class), mock(InvitationEmailSender.class), "https://srrrg.link");

	@Test
	void opensProjectForMemberWhoAcceptsExistingInvitation() {
		Project project = mock(Project.class);
		ProjectInvitation invitation = mock(ProjectInvitation.class);
		when(project.getId()).thenReturn(1L);
		when(invitation.getProject()).thenReturn(project);
		when(invitation.isUsable(any())).thenReturn(true);
		when(invitations.findActiveByTokenHash(InvitationTokenHash.sha256("token"))).thenReturn(Optional.of(invitation));
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(mock(ProjectMember.class)));

		ProjectInvitationService.AcceptedInvitation result = service.accept(2L, "token");

		assertThat(result).isEqualTo(new ProjectInvitationService.AcceptedInvitation(1L, true));
		verify(members, never()).save(any());
		verify(invitation).accept();
	}

	@Test
	void blocksDuplicateActiveInvitation() {
		ProjectMember owner = mock(ProjectMember.class);
		Project project = mock(Project.class);
		ProjectInvitation existing = mock(ProjectInvitation.class);
		when(owner.getRole()).thenReturn(ProjectRole.OWNER);
		when(owner.getProject()).thenReturn(project);
		when(projects.findById(1L)).thenReturn(Optional.of(project));
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(owner));
		when(invitations.findByProjectIdAndEmailAndCancelledAtIsNullAndAcceptedAtIsNull(1L, "invitee@example.com"))
				.thenReturn(Optional.of(existing));
		when(existing.isUsable(any())).thenReturn(true);

		assertThatThrownBy(() -> service.invite(2L, 1L, "invitee@example.com", ProjectRole.EDITOR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("이미 활성 상태인 초대가 있습니다.");
		verify(invitations, never()).saveAndFlush(any());
	}

	@Test
	void acceptsInvitationWithoutUsingEmailAsIdentity() {
		Project project = mock(Project.class);
		ProjectInvitation invitation = mock(ProjectInvitation.class);
		User userWithoutEmail = mock(User.class);
		when(project.getId()).thenReturn(1L);
		when(invitation.getProject()).thenReturn(project);
		when(invitation.getRole()).thenReturn(ProjectRole.VIEWER);
		when(invitation.isUsable(any())).thenReturn(true);
		when(invitations.findActiveByTokenHash(InvitationTokenHash.sha256("token"))).thenReturn(Optional.of(invitation));
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.empty());
		when(users.findById(2L)).thenReturn(Optional.of(userWithoutEmail));
		when(userWithoutEmail.getId()).thenReturn(2L);

		service.accept(2L, "token");

		verify(members).save(any(ProjectMember.class));
		verify(invitation).accept();
	}

	@Test
	void previewsUsableInvitationWithoutExposingRecipientEmail() {
		Project project = mock(Project.class);
		ProjectInvitation invitation = mock(ProjectInvitation.class);
		Instant expiresAt = Instant.parse("2026-08-08T00:00:00Z");
		when(invitations.findActiveByTokenHash(InvitationTokenHash.sha256("token"))).thenReturn(Optional.of(invitation));
		when(invitation.getProject()).thenReturn(project);
		when(project.getName()).thenReturn("초대 프로젝트");
		when(invitation.getRole()).thenReturn(ProjectRole.EDITOR);
		when(invitation.getExpiresAt()).thenReturn(expiresAt);
		when(invitation.isUsable(any())).thenReturn(true);

		assertThat(service.preview("token")).isEqualTo(
				new ProjectInvitationService.InvitationPreview(true, "초대 프로젝트", ProjectRole.EDITOR, expiresAt));
	}

	@Test
	void hidesExpiredInvitationDetails() {
		ProjectInvitation invitation = mock(ProjectInvitation.class);
		when(invitations.findActiveByTokenHash(InvitationTokenHash.sha256("token"))).thenReturn(Optional.of(invitation));
		when(invitation.isUsable(any())).thenReturn(false);

		assertThat(service.preview("token")).isEqualTo(
				new ProjectInvitationService.InvitationPreview(false, null, null, null));
	}

	@Test
	void rejectsInvitationForDeletedProject() {
		// 삭제된 프로젝트의 초대는 repository의 project join에서 걸러져 아예 조회되지 않는다.
		when(invitations.findActiveByTokenHash(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.accept(2L, "token"))
				.isInstanceOf(IllegalArgumentException.class);
		verify(members, never()).save(any());
	}
}
