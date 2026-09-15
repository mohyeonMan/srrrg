package link.srrrg.statistics;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;

import link.srrrg.campaign.Campaign;
import link.srrrg.campaign.CampaignRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.project.ApiKeyPrincipal;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.Project;
import link.srrrg.project.ProjectAccessService;
import link.srrrg.statistics.StatisticsResponse.Bucket;

class StatisticsServiceTest {
	private final StatisticsReportService reports = mock(StatisticsReportService.class);
	private final LinkManagementService linkManagementService = mock(LinkManagementService.class);
	private final LinkRepository links = mock(LinkRepository.class);
	private final CampaignRepository campaigns = mock(CampaignRepository.class);
	private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
	private final StatisticsService service = new StatisticsService(
			reports, linkManagementService, links, campaigns, projectAccessService);

	@Test
	void anonymousStatisticsReuseTheLinkVerifiedBySecretKey() {
		Link link = mock(Link.class);
		when(link.getId()).thenReturn(11L);
		when(linkManagementService.requireManagedLink("aB3x9Q", "secret")).thenReturn(link);

		service.anonymousLink("aB3x9Q", "secret", null, null, Bucket.DAY, 0, 50);

		verify(linkManagementService).requireManagedLink("aB3x9Q", "secret");
		verify(reports).link(11L, "aB3x9Q", null, null, Bucket.DAY, 0, 50);
		verify(links, never()).findByCodeAndProjectIsNull("aB3x9Q");
	}

	@Test
	void publicCampaignStatisticsRejectAnotherProjectBeforeAggregation() {
		Project project = mock(Project.class);
		when(project.getId()).thenReturn(8L);
		Campaign campaign = mock(Campaign.class);
		when(campaign.getProject()).thenReturn(project);
		when(campaigns.findById(3L)).thenReturn(java.util.Optional.of(campaign));
		ApiKeyPrincipal principal = new ApiKeyPrincipal(1L, 7L, Set.of(ApiKeyScope.STATS_READ));

		assertThrows(SecurityException.class,
				() -> service.publicCampaign(principal, 3L, null, null, Bucket.DAY, 0, 50));
		verify(reports, never()).campaign(3L, null, null, null, Bucket.DAY, 0, 50);
	}
}
