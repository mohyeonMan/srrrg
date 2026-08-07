package link.srrrg.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.domain.ProjectDomain;
import link.srrrg.domain.ProjectDomainService;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.SecretKeyManager;
import link.srrrg.link.management.LinkManagementService;

class ProjectServiceTest {
	private final ProjectRepository projects = mock(ProjectRepository.class);
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final ProjectInvitationRepository invitations = mock(ProjectInvitationRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final LinkRepository links = mock(LinkRepository.class);
	private final SecretKeyManager secretKeys = mock(SecretKeyManager.class);
	private final LinkManagementService linkManagement = mock(LinkManagementService.class);
	private final ProjectDomainService domains = mock(ProjectDomainService.class);
	private final ProjectService service = new ProjectService(projects, members, invitations, users, links,
			mock(SecureRandomStringGenerator.class), mock(InvitationEmailSender.class), secretKeys, linkManagement, domains, "https://srrrg.link");

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

		verify(domains).create(project);
		verify(members).save(any(ProjectMember.class));
	}

	@Test
	void blocksDemotionOfLastOwner() {
		ProjectMember owner = mock(ProjectMember.class);
		Project project = mock(Project.class);
		when(owner.getRole()).thenReturn(ProjectRole.OWNER);
		when(owner.getProject()).thenReturn(project);
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
		when(owner.getProject()).thenReturn(project);
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
		Project project = mock(Project.class);
		when(viewer.getRole()).thenReturn(ProjectRole.VIEWER);
		when(viewer.getProject()).thenReturn(project);
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.of(viewer));

		assertThatThrownBy(() -> service.importAnonymousLink(2L, 1L, "aB3x9Q", "secret"))
				.isInstanceOf(SecurityException.class);
		verify(links, never()).lockAnonymousByCode(any());
	}

	@Test
	void importsAnonymousLinkOnlyForEditorAndRevokesSecret() {
		ProjectMember editor = mock(ProjectMember.class);
		Project project = mock(Project.class);
		Link link = mock(Link.class);
		ProjectDomain domain = mock(ProjectDomain.class);
		User user = mock(User.class);
		when(editor.getRole()).thenReturn(ProjectRole.EDITOR);
		when(editor.getProject()).thenReturn(project);
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.of(editor));
		when(links.lockAnonymousByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.getProject()).thenReturn(null);
		when(link.getSecretKeyHash()).thenReturn("hash");
		when(secretKeys.matches("srrrg_sk_secret", "hash")).thenReturn(true);
		when(projects.findById(1L)).thenReturn(Optional.of(project));
		when(domains.get(1L)).thenReturn(domain);
		when(users.findById(2L)).thenReturn(Optional.of(user));

		service.importAnonymousLink(2L, 1L, "aB3x9Q", "srrrg_sk_secret");

		verify(link).assignToProject(project, domain, user);
		verify(links).lockAnonymousByCode("aB3x9Q");
		verify(links).flush();
	}

	@Test
	void previewsUsableInvitationWithoutExposingRecipientEmail() {
		Project project = mock(Project.class);
		ProjectInvitation invitation = mock(ProjectInvitation.class);
		Instant expiresAt = Instant.parse("2026-08-08T00:00:00Z");
		when(invitations.findByTokenHash(InvitationTokenHash.sha256("token"))).thenReturn(Optional.of(invitation));
		when(invitation.getProject()).thenReturn(project);
		when(project.getName()).thenReturn("초대 프로젝트");
		when(invitation.getRole()).thenReturn(ProjectRole.EDITOR);
		when(invitation.getExpiresAt()).thenReturn(expiresAt);
		when(invitation.isUsable(any())).thenReturn(true);

		assertThat(service.invitationPreview("token")).isEqualTo(
				new ProjectService.InvitationPreview(true, "초대 프로젝트", ProjectRole.EDITOR, expiresAt));
	}

	@Test
	void hidesExpiredInvitationDetails() {
		Project project = mock(Project.class);
		ProjectInvitation invitation = mock(ProjectInvitation.class);
		when(invitations.findByTokenHash(InvitationTokenHash.sha256("token"))).thenReturn(Optional.of(invitation));
		when(invitation.getProject()).thenReturn(project);
		when(invitation.isUsable(any())).thenReturn(false);

		assertThat(service.invitationPreview("token")).isEqualTo(
				new ProjectService.InvitationPreview(false, null, null, null));
	}

	@Test
	void rejectsClaimWhenTargetDomainAlreadyHasTheCode() {
		ProjectMember editor = mock(ProjectMember.class);
		Project project = mock(Project.class);
		ProjectDomain domain = mock(ProjectDomain.class);
		Link link = mock(Link.class);
		User user = mock(User.class);
		when(editor.getRole()).thenReturn(ProjectRole.EDITOR);
		when(editor.getProject()).thenReturn(project);
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.of(editor));
		when(links.lockAnonymousByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.getSecretKeyHash()).thenReturn("hash");
		when(secretKeys.matches("secret", "hash")).thenReturn(true);
		when(projects.findById(1L)).thenReturn(Optional.of(project));
		when(domains.get(1L)).thenReturn(domain);
		when(users.findById(2L)).thenReturn(Optional.of(user));
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate code"))
				.when(links).flush();

		assertThatThrownBy(() -> service.importAnonymousLink(2L, 1L, "aB3x9Q", "secret"))
				.isInstanceOf(link.srrrg.link.LinkCodeConflictException.class);
	}

	@Test
	void rejectsReservedProjectSlug() {
		when(members.countByIdUserIdAndRoleAndProjectArchivedAtIsNull(2L, ProjectRole.OWNER)).thenReturn(0L);
		when(users.findById(2L)).thenReturn(Optional.of(mock(User.class)));
		when(domains.isReservedSlug("admin")).thenReturn(true);
		when(domains.isReservedSlug("cname")).thenReturn(true);

		assertThatThrownBy(() -> service.create(2L, "관리", "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.create(2L, "DNS", "cname"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void archivesProjectForOwner() {
		Project project = mock(Project.class);
		ProjectMember owner = mock(ProjectMember.class);
		when(owner.getRole()).thenReturn(ProjectRole.OWNER);
		when(owner.getProject()).thenReturn(project);
		when(members.findByIdProjectIdAndIdUserId(1L, 2L)).thenReturn(Optional.of(owner));

		service.archive(2L, 1L);

		verify(project).archive();
	}

	@Test
	void rejectsInvitationForArchivedProject() {
		ProjectInvitation invitation = mock(ProjectInvitation.class);
		Project project = mock(Project.class);
		when(invitations.findByTokenHash(any())).thenReturn(Optional.of(invitation));
		when(invitation.getProject()).thenReturn(project);
		when(project.getId()).thenReturn(1L);
		when(project.getArchivedAt()).thenReturn(Instant.now());

		assertThatThrownBy(() -> service.accept(2L, "token"))
				.isInstanceOf(IllegalStateException.class);
		verify(members, never()).save(any());
		verify(invitation, never()).accept();
	}

	@Test
	void reportsSlugConstraintRaceAsInvalidRequest() {
		when(members.countByIdUserIdAndRoleAndProjectArchivedAtIsNull(2L, ProjectRole.OWNER)).thenReturn(0L);
		when(users.findById(2L)).thenReturn(Optional.of(mock(User.class)));
		when(projects.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate slug"));

		assertThatThrownBy(() -> service.create(2L, "프로젝트", "available"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("이미 사용 중인 프로젝트 slug입니다.");
		verify(projects, times(1)).saveAndFlush(any());
	}
}
