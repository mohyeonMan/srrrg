package link.srrrg.project;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.SecretKeyManager;

class ProjectServiceTest {
	private final ProjectRepository projects = mock(ProjectRepository.class);
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final ProjectInvitationRepository invitations = mock(ProjectInvitationRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final LinkRepository links = mock(LinkRepository.class);
	private final SecretKeyManager secretKeys = mock(SecretKeyManager.class);
	private final ProjectService service = new ProjectService(projects, members, invitations, users, links,
			mock(SecureRandomStringGenerator.class), mock(InvitationEmailSender.class), secretKeys, "https://srrrg.link");

	@Test
	void createsPersonalProjectForUserWithoutMembership() {
		User user = mock(User.class);
		Project project = mock(Project.class);
		when(members.findByIdUserId(2L)).thenReturn(java.util.List.of());
		when(members.countByIdUserIdAndRole(2L, ProjectRole.OWNER)).thenReturn(0L);
		when(users.findById(2L)).thenReturn(Optional.of(user));
		when(projects.saveAndFlush(any(Project.class))).thenReturn(project);
		when(user.getId()).thenReturn(2L);
		when(project.getId()).thenReturn(1L);

		service.ensurePersonalProject(2L);

		verify(members).save(any(ProjectMember.class));
	}

	@Test
	void blocksDemotionOfLastOwner() {
		ProjectMember owner = mock(ProjectMember.class);
		when(owner.getRole()).thenReturn(ProjectRole.OWNER);
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.of(owner));
		when(members.lockByProjectAndUser(1L, 3L)).thenReturn(Optional.of(owner));
		when(members.countByIdProjectIdAndRole(1L, ProjectRole.OWNER)).thenReturn(1L);

		assertThatThrownBy(() -> service.changeMemberRole(2L, 1L, 3L, ProjectRole.EDITOR))
				.isInstanceOf(IllegalStateException.class);
		verify(owner, never()).changeRole(any());
	}

	@Test
	void opensProjectForMemberWhoAcceptsExistingInvitation() {
		Project project = mock(Project.class);
		ProjectInvitation invitation = mock(ProjectInvitation.class);
		when(project.getId()).thenReturn(1L);
		when(invitation.getProject()).thenReturn(project);
		when(invitation.isUsable(any())).thenReturn(true);
		when(invitations.findByTokenHash(InvitationTokenHash.sha256("token"))).thenReturn(Optional.of(invitation));
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.of(mock(ProjectMember.class)));

		ProjectService.AcceptedInvitation result = service.accept(2L, "token");

		org.assertj.core.api.Assertions.assertThat(result).isEqualTo(new ProjectService.AcceptedInvitation(1L, true));
		verify(members, never()).save(any());
		verify(invitation).accept();
	}

	@Test
	void blocksDuplicateActiveInvitation() {
		ProjectMember owner = mock(ProjectMember.class);
		Project project = mock(Project.class);
		ProjectInvitation existing = mock(ProjectInvitation.class);
		when(owner.getRole()).thenReturn(ProjectRole.OWNER);
		when(projects.findById(1L)).thenReturn(Optional.of(project));
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.of(owner));
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
		when(invitations.findByTokenHash(InvitationTokenHash.sha256("token"))).thenReturn(Optional.of(invitation));
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.empty());
		when(users.findById(2L)).thenReturn(Optional.of(userWithoutEmail));
		when(userWithoutEmail.getId()).thenReturn(2L);

		service.accept(2L, "token");

		verify(members).save(any(ProjectMember.class));
		verify(invitation).accept();
	}

	@Test
	void blocksViewerFromImportingAnonymousLink() {
		ProjectMember viewer = mock(ProjectMember.class);
		when(viewer.getRole()).thenReturn(ProjectRole.VIEWER);
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.of(viewer));

		assertThatThrownBy(() -> service.importAnonymousLink(2L, 1L, "aB3x9Q", "secret"))
				.isInstanceOf(SecurityException.class);
		verify(links, never()).findByCode(any());
	}

	@Test
	void importsAnonymousLinkOnlyForEditorAndRevokesSecret() {
		ProjectMember editor = mock(ProjectMember.class);
		Project project = mock(Project.class);
		Link link = mock(Link.class);
		when(editor.getRole()).thenReturn(ProjectRole.EDITOR);
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.of(editor));
		when(links.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.getProject()).thenReturn(null);
		when(link.getSecretKeyHash()).thenReturn("hash");
		when(secretKeys.matches("srrrg_sk_secret", "hash")).thenReturn(true);
		when(projects.findById(1L)).thenReturn(Optional.of(project));

		service.importAnonymousLink(2L, 1L, "aB3x9Q", "srrrg_sk_secret");

		verify(link).assignToProject(project);
	}
}
