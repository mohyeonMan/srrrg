package link.srrrg.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import link.srrrg.domain.ProjectDomainService;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.project.ProjectMemberRepository;

class CampaignLinkCreationServiceTest {

	private final CampaignService campaignService = mock(CampaignService.class);
	private final UtmTemplateFieldRepository fields = mock(UtmTemplateFieldRepository.class);
	private final CampaignUtmDefaultRepository defaults = mock(CampaignUtmDefaultRepository.class);
	private final LinkManagementService linkManagement = mock(LinkManagementService.class);
	private final ProjectDomainService domains = mock(ProjectDomainService.class);
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final link.srrrg.common.ratelimit.RateLimitService rateLimitService = mock(link.srrrg.common.ratelimit.RateLimitService.class);
	private CampaignLinkCreationService service;
	private Campaign campaign;
	private UtmTemplate template;
	private UtmTemplateField sourceField;
	private UtmTemplateField mediumField;

	@BeforeEach
	void setUp() {
		service = new CampaignLinkCreationService(campaignService, fields, defaults, linkManagement, domains, members, rateLimitService);
		campaign = mock(Campaign.class);
		when(campaign.getId()).thenReturn(100L);
		template = mock(UtmTemplate.class);
		when(template.getId()).thenReturn(11L);
		sourceField = mock(UtmTemplateField.class);
		when(sourceField.getName()).thenReturn("utm_source");
		mediumField = mock(UtmTemplateField.class);
		when(mediumField.getName()).thenReturn("utm_medium");
		when(fields.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(11L)).thenReturn(List.of(mediumField, sourceField));
	}

	@Test
	void requestValueOverridesCampaignDefault() {
		CampaignUtmDefault sourceDefault = mock(CampaignUtmDefault.class);
		when(sourceDefault.getField()).thenReturn(sourceField);
		when(sourceDefault.getDefaultValue()).thenReturn("default-source");
		when(defaults.findByCampaignIdOrderByFieldNameAsc(100L)).thenReturn(List.of(sourceDefault));

		Map<UtmTemplateField, String> resolved = service.resolveUtmValues(campaign, template, Map.of("utm_source", "request-source"));

		assertThat(resolved).containsEntry(sourceField, "request-source");
	}

	@Test
	void fallsBackToCampaignDefaultWhenRequestOmitsField() {
		CampaignUtmDefault sourceDefault = mock(CampaignUtmDefault.class);
		when(sourceDefault.getField()).thenReturn(sourceField);
		when(sourceDefault.getDefaultValue()).thenReturn("default-source");
		when(defaults.findByCampaignIdOrderByFieldNameAsc(100L)).thenReturn(List.of(sourceDefault));

		Map<UtmTemplateField, String> resolved = service.resolveUtmValues(campaign, template, Map.of());

		assertThat(resolved).containsEntry(sourceField, "default-source");
	}

	@Test
	void explicitNullRequestValueSkipsDefaultEntirely() {
		CampaignUtmDefault sourceDefault = mock(CampaignUtmDefault.class);
		when(sourceDefault.getField()).thenReturn(sourceField);
		when(sourceDefault.getDefaultValue()).thenReturn("default-source");
		when(defaults.findByCampaignIdOrderByFieldNameAsc(100L)).thenReturn(List.of(sourceDefault));

		java.util.Map<String, String> requestValues = new java.util.HashMap<>();
		requestValues.put("utm_source", null);
		Map<UtmTemplateField, String> resolved = service.resolveUtmValues(campaign, template, requestValues);

		assertThat(resolved).doesNotContainKey(sourceField);
	}

	@Test
	void rejectsUnknownUtmFieldNameInRequest() {
		when(defaults.findByCampaignIdOrderByFieldNameAsc(100L)).thenReturn(List.of());

		assertThatThrownBy(() -> service.resolveUtmValues(campaign, template, Map.of("not_a_field", "value")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsUtmValuesWhenCampaignHasNoTemplate() {
		assertThatThrownBy(() -> service.resolveUtmValues(campaign, null, Map.of("utm_source", "value")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void returnsEmptyMapWhenNoTemplateAndNoRequestValues() {
		Map<UtmTemplateField, String> resolved = service.resolveUtmValues(campaign, null, Map.of());
		assertThat(resolved).isEmpty();
	}
}
