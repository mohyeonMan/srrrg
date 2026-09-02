package link.srrrg.link.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import link.srrrg.common.ratelimit.RateLimitService;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkCodeConflictException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.SecretKeyManager;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import link.srrrg.project.Project;
import link.srrrg.project.ProjectAccessService;
import link.srrrg.project.ProjectMember;
import link.srrrg.project.ProjectMemberRepository;
import link.srrrg.project.ProjectRepository;
import link.srrrg.project.ProjectRole;

class ProjectLinkServiceTest {
	private final ProjectRepository projects = mock(ProjectRepository.class);
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final LinkRepository links = mock(LinkRepository.class);
	private final SecretKeyManager secretKeyManager = mock(SecretKeyManager.class);
	private final LinkManagementService linkManagementService = mock(LinkManagementService.class);
	private final ProjectAccessService projectAccessService = new ProjectAccessService(members);
	private final RateLimitService rateLimitService = mock(RateLimitService.class);
	private final ProjectLinkService projectLinkService = new ProjectLinkService(projects, users, links,
			secretKeyManager, linkManagementService, projectAccessService, rateLimitService);

	@Test
	void blocksViewerFromClaimingAnonymousLink() {
		ProjectMember viewer = mock(ProjectMember.class);
		when(viewer.getRole()).thenReturn(ProjectRole.VIEWER);
		when(viewer.getProject()).thenReturn(mock(Project.class));
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(viewer));

		assertThatThrownBy(() -> projectLinkService.claimAnonymousForWeb(2L, 1L, "aB3x9Q", "secret"))
				.isInstanceOf(SecurityException.class);
		verify(links, never()).lockAnonymousByCode(any());
	}

	@Test
	void claimsAnonymousLinkOnlyForEditorAndRevokesSecret() {
		ProjectMember editor = mock(ProjectMember.class);
		Project project = mock(Project.class);
		Link link = mock(Link.class);
		User user = mock(User.class);
		when(editor.getRole()).thenReturn(ProjectRole.EDITOR);
		when(editor.getProject()).thenReturn(project);
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(editor));
		when(links.lockAnonymousByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.getProject()).thenReturn(null);
		when(link.getSecretKeyHash()).thenReturn("hash");
		when(secretKeyManager.matches("srrrg_sk_secret", "hash")).thenReturn(true);
		when(projects.findById(1L)).thenReturn(Optional.of(project));
		when(users.findById(2L)).thenReturn(Optional.of(user));

		projectLinkService.claimAnonymousForWeb(2L, 1L, "aB3x9Q", "srrrg_sk_secret");

		verify(link).assignToProject(project, user);
		verify(links).flush();
	}

	@Test
	void editorDeletesProjectOwnedLink() {
		ProjectMember editor = mock(ProjectMember.class);
		Link link = mock(Link.class);
		when(editor.getRole()).thenReturn(ProjectRole.EDITOR);
		when(editor.getProject()).thenReturn(mock(Project.class));
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(editor));
		when(links.findByProjectIdAndCode(1L, "aB3x9Q")).thenReturn(Optional.of(link));

		projectLinkService.deleteForWeb(2L, 1L, "aB3x9Q");

		verify(links).delete(link);
	}

	@Test
	void viewerCannotDeleteProjectOwnedLink() {
		ProjectMember viewer = mock(ProjectMember.class);
		when(viewer.getRole()).thenReturn(ProjectRole.VIEWER);
		when(viewer.getProject()).thenReturn(mock(Project.class));
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(viewer));

		assertThatThrownBy(() -> projectLinkService.deleteForWeb(2L, 1L, "aB3x9Q"))
				.isInstanceOf(SecurityException.class);
		verify(links, never()).findByProjectIdAndCode(any(), any());
	}

	@Test
	void viewerCanOpenProjectLinkButSettingsAreReadOnly() {
		ProjectMember viewer = mock(ProjectMember.class);
		Link link = mock(Link.class);
		when(viewer.getRole()).thenReturn(ProjectRole.VIEWER);
		when(viewer.getProject()).thenReturn(mock(Project.class));
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(viewer));
		when(links.findByProjectIdAndCode(1L, "aB3x9Q")).thenReturn(Optional.of(link));

		projectLinkService.detailForWeb(2L, 1L, "aB3x9Q");

		verify(linkManagementService).projectManagementResponse(link, false);
	}

	@Test
	void editorCanUpdateProjectOwnedLink() {
		ProjectMember editor = mock(ProjectMember.class);
		Link link = mock(Link.class);
		UpdateLinkRequest request = new UpdateLinkRequest();
		when(editor.getRole()).thenReturn(ProjectRole.EDITOR);
		when(editor.getProject()).thenReturn(mock(Project.class));
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(editor));
		when(links.findByProjectIdAndCode(1L, "aB3x9Q")).thenReturn(Optional.of(link));

		projectLinkService.updateForWeb(2L, 1L, "aB3x9Q", request);

		verify(linkManagementService).updateProjectLink(link, request);
	}

	@Test
	void reportsProjectCodeConflictWhenClaimFlushFails() {
		ProjectMember editor = mock(ProjectMember.class);
		Project project = mock(Project.class);
		Link link = mock(Link.class);
		when(editor.getRole()).thenReturn(ProjectRole.EDITOR);
		when(editor.getProject()).thenReturn(project);
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(editor));
		when(links.lockAnonymousByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.getSecretKeyHash()).thenReturn("hash");
		when(secretKeyManager.matches("secret", "hash")).thenReturn(true);
		when(projects.findById(1L)).thenReturn(Optional.of(project));
		when(users.findById(2L)).thenReturn(Optional.of(mock(User.class)));
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate code")).when(links).flush();

		assertThatThrownBy(() -> projectLinkService.claimAnonymousForWeb(2L, 1L, "aB3x9Q", "secret"))
				.isInstanceOf(LinkCodeConflictException.class);
	}

	@Test
	void apiKeyListReadsOneExtraItemAfterCursor() {
		Link link = mock(Link.class);
		when(links.findByProjectIdAndCampaignIsNullAndIdLessThanOrderByIdDesc(any(), any(), any()))
				.thenReturn(List.of(link));

		assertThat(projectLinkService.listForApiKey(7L, 10L, 1)).containsExactly(link);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(links).findByProjectIdAndCampaignIsNullAndIdLessThanOrderByIdDesc(
				org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(10L), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
	}
}
