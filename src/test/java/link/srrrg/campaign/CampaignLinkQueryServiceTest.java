package link.srrrg.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;

import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.LinkUtmValueRepository;
import link.srrrg.link.LinkUtmValueRepository.EffectiveUtmValueByLink;

class CampaignLinkQueryServiceTest {
	private final CampaignService campaignService = mock(CampaignService.class);
	private final LinkRepository linkRepository = mock(LinkRepository.class);
	private final LinkUtmValueRepository linkUtmValueRepository = mock(LinkUtmValueRepository.class);
	private final CampaignLinkQueryService service = new CampaignLinkQueryService(
			campaignService, linkRepository, linkUtmValueRepository);

	@Test
	void webListChecksAccessAndLoadsUtmOnlyForVisibleLinks() {
		Link first = link(30L);
		Link second = link(20L);
		Link sentinel = link(10L);
		EffectiveUtmValueByLink effective = mock(EffectiveUtmValueByLink.class);
		when(effective.getLinkId()).thenReturn(30L);
		when(effective.getFieldName()).thenReturn("utm_source");
		when(effective.getValue()).thenReturn("newsletter");
		when(effective.getSource()).thenReturn("CAMPAIGN_DEFAULT");
		when(linkRepository.findByCampaignIdOrderByIdDesc(5L, PageRequest.of(0, 3)))
				.thenReturn(List.of(first, second, sentinel));
		when(linkUtmValueRepository.findEffectiveByLinkIds(List.of(30L, 20L)))
				.thenReturn(List.of(effective));

		CampaignLinkQueryResult result = service.listForUser(1L, 5L, null, 2);

		assertEquals(List.of(first, second), result.items());
		assertEquals(20L, result.nextCursor());
		assertEquals(List.of(new CampaignEffectiveUtmValue(
				30L, "utm_source", "newsletter", "CAMPAIGN_DEFAULT")), result.effectiveUtmValues());
		InOrder order = inOrder(campaignService, linkRepository);
		order.verify(campaignService).get(1L, 5L);
		order.verify(linkRepository).findByCampaignIdOrderByIdDesc(5L, PageRequest.of(0, 3));
	}

	@Test
	void publicListUsesProjectScopedCampaignCheckAndSkipsWebUtmQuery() {
		Link link = link(20L);
		when(linkRepository.findByCampaignIdAndIdLessThanOrderByIdDesc(5L, 30L, PageRequest.of(0, 3)))
				.thenReturn(List.of(link));

		CampaignLinkQueryResult result = service.listForApiKey(7L, 5L, 30L, 2);

		assertEquals(List.of(link), result.items());
		assertEquals(null, result.nextCursor());
		assertEquals(List.of(), result.effectiveUtmValues());
		verify(campaignService).findForApiKey(7L, 5L);
		verify(linkUtmValueRepository, never()).findEffectiveByLinkIds(List.of(20L));
	}

	@Test
	void rejectedCampaignAccessStopsBeforeLinkRepositoryQuery() {
		SecurityException denied = new SecurityException("denied");
		when(campaignService.get(1L, 5L)).thenThrow(denied);

		assertSame(denied, assertThrows(SecurityException.class,
				() -> service.listForUser(1L, 5L, null, 50)));
		verify(linkRepository, never()).findByCampaignIdOrderByIdDesc(5L, PageRequest.of(0, 51));
	}

	private Link link(Long id) {
		Link link = mock(Link.class);
		when(link.getId()).thenReturn(id);
		return link;
	}
}
