package link.srrrg.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import link.srrrg.link.management.LinkManagementService;
import link.srrrg.project.ProjectAccessService;
import link.srrrg.project.ProjectMemberRepository;

class CampaignLinkCreationServiceTest {

	private final CampaignService campaignService = mock(CampaignService.class);
	private final UtmTemplateFieldRepository fields = mock(UtmTemplateFieldRepository.class);
	private final LinkManagementService linkManagement = mock(LinkManagementService.class);
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final ProjectAccessService projectAccess = new ProjectAccessService(members);
	private final link.srrrg.common.ratelimit.RateLimitService rateLimitService = mock(link.srrrg.common.ratelimit.RateLimitService.class);
	private CampaignLinkCreationService service;
	private UtmTemplate template;
	private UtmTemplateField sourceField;
	private UtmTemplateField mediumField;

	@BeforeEach
	void setUp() {
		service = new CampaignLinkCreationService(campaignService, fields, linkManagement, projectAccess, rateLimitService);
		template = mock(UtmTemplate.class);
		when(template.getId()).thenReturn(11L);
		sourceField = mock(UtmTemplateField.class);
		when(sourceField.getName()).thenReturn("utm_source");
		mediumField = mock(UtmTemplateField.class);
		when(mediumField.getName()).thenReturn("utm_medium");
		when(fields.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(11L)).thenReturn(List.of(mediumField, sourceField));
	}

	@Test
	void storesExplicitRequestValue() {
		Map<String, String> resolved = service.resolveUtmValues(template, Map.of("utm_source", "request-source"));

		assertThat(resolved).containsEntry("utm_source", "request-source");
	}

	@Test
	void omittedFieldIsNotCopiedFromCampaignDefault() {
		Map<String, String> resolved = service.resolveUtmValues(template, Map.of());

		assertThat(resolved).isEmpty();
	}

	@Test
	void explicitNullRequestValueSkipsDefaultEntirely() {
		java.util.Map<String, String> requestValues = new java.util.HashMap<>();
		requestValues.put("utm_source", null);
		Map<String, String> resolved = service.resolveUtmValues(template, requestValues);

		assertThat(resolved).doesNotContainKey("utm_source");
	}

	@Test
	void rejectsUnknownUtmFieldNameInRequest() {
		assertThatThrownBy(() -> service.resolveUtmValues(template, Map.of("not_a_field", "value")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsUtmValuesWhenCampaignHasNoTemplate() {
		assertThatThrownBy(() -> service.resolveUtmValues(null, Map.of("utm_source", "value")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void returnsEmptyMapWhenNoTemplateAndNoRequestValues() {
		Map<String, String> resolved = service.resolveUtmValues(null, Map.of());
		assertThat(resolved).isEmpty();
	}
}
