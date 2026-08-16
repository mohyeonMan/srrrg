package link.srrrg.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import link.srrrg.identity.User;
import link.srrrg.project.Project;
import link.srrrg.project.ProjectMember;
import link.srrrg.project.ProjectMemberRepository;
import link.srrrg.project.ProjectRepository;
import link.srrrg.project.ProjectRole;

class UtmTemplateServiceTest {

	private final UtmTemplateRepository templates = mock(UtmTemplateRepository.class);
	private final UtmTemplateFieldRepository fields = mock(UtmTemplateFieldRepository.class);
	private final CampaignRepository campaigns = mock(CampaignRepository.class);
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final ProjectRepository projects = mock(ProjectRepository.class);
	private UtmTemplateService service;

	@BeforeEach
	void setUp() {
		service = new UtmTemplateService(templates, fields, campaigns, members, projects);
	}

	@Test
	void rejectsAddingFieldBeyondMaxActive() {
		Project project = mock(Project.class);
		when(project.getId()).thenReturn(1L);
		ProjectMember membership = mockMembership(project, ProjectRole.EDITOR);
		when(members.findActiveByProjectAndUser(1L, 5L)).thenReturn(Optional.of(membership));

		UtmTemplate template = mock(UtmTemplate.class);
		when(template.isDeleted()).thenReturn(false);
		when(templates.lockByIdAndProjectId(10L, 1L)).thenReturn(Optional.of(template));
		when(fields.countByUtmTemplateIdAndDeletedAtIsNull(10L)).thenReturn((long) UtmTemplateService.MAX_ACTIVE_FIELDS);

		assertThatThrownBy(() -> service.addField(5L, 1L, 10L, "utm_source"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("최대");
	}

	@Test
	void rejectsInvalidFieldNameFormat() {
		Project project = mock(Project.class);
		when(project.getId()).thenReturn(1L);
		ProjectMember membership = mockMembership(project, ProjectRole.EDITOR);
		when(members.findActiveByProjectAndUser(1L, 5L)).thenReturn(Optional.of(membership));

		UtmTemplate template = mock(UtmTemplate.class);
		when(template.isDeleted()).thenReturn(false);
		when(templates.lockByIdAndProjectId(10L, 1L)).thenReturn(Optional.of(template));
		when(fields.countByUtmTemplateIdAndDeletedAtIsNull(10L)).thenReturn(0L);

		assertThatThrownBy(() -> service.addField(5L, 1L, 10L, "UTM-Source!"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void blocksDeletingTemplateStillUsedByActiveCampaign() {
		Project project = mock(Project.class);
		when(project.getId()).thenReturn(1L);
		ProjectMember membership = mockMembership(project, ProjectRole.EDITOR);
		when(members.findActiveByProjectAndUser(1L, 5L)).thenReturn(Optional.of(membership));

		UtmTemplate template = mock(UtmTemplate.class);
		when(template.isDeleted()).thenReturn(false);
		when(templates.findByIdAndProjectId(10L, 1L)).thenReturn(Optional.of(template));
		when(campaigns.countByUtmTemplateId(10L)).thenReturn(1L);

		assertThatThrownBy(() -> service.delete(5L, 1L, 10L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("사용 중인 캠페인");
	}

	@Test
	void viewerCannotAddField() {
		Project project = mock(Project.class);
		when(project.getId()).thenReturn(1L);
		ProjectMember membership = mockMembership(project, ProjectRole.VIEWER);
		when(members.findActiveByProjectAndUser(1L, 5L)).thenReturn(Optional.of(membership));

		assertThatThrownBy(() -> service.addField(5L, 1L, 10L, "utm_source"))
				.isInstanceOf(SecurityException.class);
	}

	private ProjectMember mockMembership(Project project, ProjectRole role) {
		ProjectMember membership = mock(ProjectMember.class);
		when(membership.getProject()).thenReturn(project);
		when(membership.getRole()).thenReturn(role);
		return membership;
	}
}
