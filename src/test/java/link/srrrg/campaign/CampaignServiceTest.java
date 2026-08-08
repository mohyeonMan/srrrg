package link.srrrg.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import link.srrrg.campaign.importing.CampaignImportRepository;
import link.srrrg.identity.UserRepository;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.UrlValidator;
import link.srrrg.project.Project;
import link.srrrg.project.ProjectMember;
import link.srrrg.project.ProjectMemberRepository;
import link.srrrg.project.ProjectRepository;
import link.srrrg.project.ProjectRole;

class CampaignServiceTest {

	private final CampaignRepository campaigns = mock(CampaignRepository.class);
	private final UtmTemplateRepository templates = mock(UtmTemplateRepository.class);
	private final UtmTemplateFieldRepository fields = mock(UtmTemplateFieldRepository.class);
	private final CampaignUtmDefaultRepository defaults = mock(CampaignUtmDefaultRepository.class);
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final ProjectRepository projects = mock(ProjectRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final LinkRepository links = mock(LinkRepository.class);
	private final CampaignImportRepository imports = mock(CampaignImportRepository.class);
	private final UrlValidator urlValidator = mock(UrlValidator.class);
	private CampaignService service;

	@BeforeEach
	void setUp() {
		service = new CampaignService(campaigns, templates, fields, defaults, members, projects, users, links, imports, urlValidator);
	}

	@Test
	void updatesDefaultOriginalUrlAfterUrlValidation() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.EDITOR);
		when(campaign.isArchived()).thenReturn(false);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));

		service.changeDefaultOriginalUrl(5L, 100L, " https://example.com/default ");

		verify(urlValidator).validate("https://example.com/default");
		verify(campaign).changeDefaultOriginalUrl("https://example.com/default");
	}

	@Test
	void allowsRemovingDefaultOriginalUrl() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.EDITOR);
		when(campaign.isArchived()).thenReturn(false);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));

		service.changeDefaultOriginalUrl(5L, 100L, null);

		verify(urlValidator, never()).validate(any());
		verify(campaign).changeDefaultOriginalUrl(null);
	}

	@Test
	void archivingCampaignSoftDeletesItsLinksAndCancelsActiveImports() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.EDITOR);
		when(campaign.getId()).thenReturn(100L);
		when(campaign.isArchived()).thenReturn(false);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));

		service.archive(5L, 100L);

		verify(campaign).archive();
		verify(links).softDeleteByCampaignId(100L);
		verify(imports).cancelActiveByCampaignId(100L);
	}

	@Test
	void archivingAlreadyArchivedCampaignReturnsGoneLikeARepeatLinkDelete() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.EDITOR);
		when(campaign.getId()).thenReturn(100L);
		when(campaign.isArchived()).thenReturn(true);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));

		assertThatThrownBy(() -> service.archive(5L, 100L)).isInstanceOf(link.srrrg.link.LinkGoneException.class);

		verify(campaign, never()).archive();
		verify(links, never()).softDeleteByCampaignId(any());
	}

	@Test
	void viewerCannotArchiveCampaign() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.VIEWER);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));

		assertThatThrownBy(() -> service.archive(5L, 100L)).isInstanceOf(SecurityException.class);
		verify(links, never()).softDeleteByCampaignId(any());
	}

	@Test
	void switchingTemplateDeletesExistingDefaultsForOldTemplate() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.EDITOR);
		when(campaign.getId()).thenReturn(100L);
		when(campaign.isArchived()).thenReturn(false);
		UtmTemplate oldTemplate = mock(UtmTemplate.class);
		when(oldTemplate.getId()).thenReturn(11L);
		when(campaign.getUtmTemplate()).thenReturn(oldTemplate);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));

		UtmTemplate newTemplate = mock(UtmTemplate.class);
		when(newTemplate.getId()).thenReturn(22L);
		when(newTemplate.isDeleted()).thenReturn(false);
		when(templates.findByIdAndProjectId(22L, 1L)).thenReturn(Optional.of(newTemplate));

		service.selectTemplate(5L, 100L, 22L);

		verify(defaults).deleteByCampaignId(100L);
		verify(campaign).selectTemplate(newTemplate);
	}

	@Test
	void selectingSameTemplateDoesNotDeleteDefaults() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.EDITOR);
		when(campaign.getId()).thenReturn(100L);
		when(campaign.isArchived()).thenReturn(false);
		UtmTemplate template = mock(UtmTemplate.class);
		when(template.getId()).thenReturn(11L);
		when(campaign.getUtmTemplate()).thenReturn(template);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));
		when(templates.findByIdAndProjectId(11L, 1L)).thenReturn(Optional.of(template));
		when(template.isDeleted()).thenReturn(false);

		service.selectTemplate(5L, 100L, 11L);

		verify(defaults, never()).deleteByCampaignId(any());
	}

	@Test
	void updatingDefaultsWithNullValueDeletesExistingDefault() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.EDITOR);
		when(campaign.getId()).thenReturn(100L);
		when(campaign.isArchived()).thenReturn(false);
		UtmTemplate template = mock(UtmTemplate.class);
		when(template.getId()).thenReturn(11L);
		when(campaign.getUtmTemplate()).thenReturn(template);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));

		UtmTemplateField field = mock(UtmTemplateField.class);
		when(field.getId()).thenReturn(50L);
		when(fields.findByUtmTemplateIdAndNameAndDeletedAtIsNull(11L, "utm_source")).thenReturn(Optional.of(field));
		CampaignUtmDefault existing = mock(CampaignUtmDefault.class);
		when(defaults.findByCampaignIdAndFieldId(100L, 50L)).thenReturn(Optional.of(existing));

		java.util.Map<String, String> updates = new java.util.HashMap<>();
		updates.put("utm_source", null);
		service.updateDefaults(5L, 100L, updates);

		verify(defaults).delete(existing);
	}

	@Test
	void rejectsUpdatingDefaultsForUnknownField() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.EDITOR);
		when(campaign.getId()).thenReturn(100L);
		when(campaign.isArchived()).thenReturn(false);
		UtmTemplate template = mock(UtmTemplate.class);
		when(template.getId()).thenReturn(11L);
		when(campaign.getUtmTemplate()).thenReturn(template);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));
		when(fields.findByUtmTemplateIdAndNameAndDeletedAtIsNull(11L, "unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateDefaults(5L, 100L, java.util.Map.of("unknown", "value")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void getThrowsGoneForArchivedCampaign() {
		Campaign campaign = campaignOwnedBy(1L, ProjectRole.VIEWER);
		when(campaign.isArchived()).thenReturn(true);
		when(campaigns.findById(100L)).thenReturn(Optional.of(campaign));

		assertThatThrownBy(() -> service.get(5L, 100L)).isInstanceOf(link.srrrg.link.LinkGoneException.class);
	}

	private Campaign campaignOwnedBy(Long projectId, ProjectRole role) {
		Project project = mock(Project.class);
		when(project.getId()).thenReturn(projectId);
		Campaign campaign = mock(Campaign.class);
		when(campaign.getProject()).thenReturn(project);
		ProjectMember membership = mock(ProjectMember.class);
		when(membership.getProject()).thenReturn(project);
		when(membership.getRole()).thenReturn(role);
		when(members.findByIdProjectIdAndIdUserId(projectId, 5L)).thenReturn(Optional.of(membership));
		return campaign;
	}
}
