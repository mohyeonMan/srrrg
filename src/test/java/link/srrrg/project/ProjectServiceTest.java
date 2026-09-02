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

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import link.srrrg.campaign.UtmTemplateService;
import link.srrrg.domain.ProjectDomainService;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;

class ProjectServiceTest {
	private final ProjectRepository projects = mock(ProjectRepository.class);
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final ProjectDomainService projectDomainService = mock(ProjectDomainService.class);
	private final UtmTemplateService utmTemplateService = mock(UtmTemplateService.class);
	private final ProjectService projectService = new ProjectService(projects, members,
			new ProjectAccessService(members), users, projectDomainService, utmTemplateService);

	@Test
	void createsPersonalProjectForUserWithoutMembership() {
		User user = mock(User.class);
		Project project = mock(Project.class);
		when(members.findActiveByUserId(2L)).thenReturn(java.util.List.of());
		when(members.countActiveByUserIdAndRole(2L, ProjectRole.OWNER)).thenReturn(0L);
		when(users.findById(2L)).thenReturn(Optional.of(user));
		when(projects.saveAndFlush(any(Project.class))).thenReturn(project);
		when(user.getId()).thenReturn(2L);
		when(project.getId()).thenReturn(1L);

		projectService.ensurePersonalProject(2L);

		verify(members).save(any(ProjectMember.class));
		verify(utmTemplateService).createDefault(project);
	}

	@Test
	void rejectsReservedProjectSlug() {
		when(members.countActiveByUserIdAndRole(2L, ProjectRole.OWNER)).thenReturn(0L);
		when(users.findById(2L)).thenReturn(Optional.of(mock(User.class)));
		when(projectDomainService.isReservedSubdomain("admin")).thenReturn(true);
		when(projectDomainService.isReservedSubdomain("cname")).thenReturn(true);

		assertThatThrownBy(() -> projectService.create(2L, "관리", "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> projectService.create(2L, "DNS", "cname"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deletesProjectForOwner() {
		Project project = mock(Project.class);
		ProjectMember owner = mock(ProjectMember.class);
		when(owner.getRole()).thenReturn(ProjectRole.OWNER);
		when(owner.getProject()).thenReturn(project);
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(owner));

		projectService.delete(2L, 1L);

		verify(projects).softDeleteById(1L);
	}

	@Test
	void ownerClaimsProjectSubdomain() {
		Project project = mock(Project.class);
		ProjectMember owner = mock(ProjectMember.class);
		when(owner.getRole()).thenReturn(ProjectRole.OWNER);
		when(owner.getProject()).thenReturn(project);
		when(project.getSubdomain()).thenReturn("before");
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(owner));
		when(projects.saveAndFlush(project)).thenReturn(project);

		assertThat(projectService.claimSubdomain(2L, 1L, " After-Domain ")).isSameAs(project);
		verify(project).claimSubdomain("after-domain");
	}

	@Test
	void viewerCannotClaimProjectSubdomain() {
		ProjectMember viewer = mock(ProjectMember.class);
		when(viewer.getRole()).thenReturn(ProjectRole.VIEWER);
		when(viewer.getProject()).thenReturn(mock(Project.class));
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(viewer));

		assertThatThrownBy(() -> projectService.claimSubdomain(2L, 1L, "after-domain"))
				.isInstanceOf(SecurityException.class);
		verify(projects, never()).saveAndFlush(any());
	}

	@Test
	void reportsSlugConstraintRaceAsInvalidRequest() {
		when(members.countActiveByUserIdAndRole(2L, ProjectRole.OWNER)).thenReturn(0L);
		when(users.findById(2L)).thenReturn(Optional.of(mock(User.class)));
		when(projects.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate slug"));

		assertThatThrownBy(() -> projectService.create(2L, "프로젝트", "available"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("이미 사용 중인 서브도메인입니다.");
		verify(projects, times(1)).saveAndFlush(any());
	}
}
