package link.srrrg.campaign.link.service;

import link.srrrg.campaign.link.model.CampaignEffectiveUtmValue;
import link.srrrg.campaign.link.model.CampaignLinkQueryResult;
import link.srrrg.campaign.service.CampaignService;
import link.srrrg.link.repository.LinkRepository;
import link.srrrg.link.repository.LinkUtmValueRepository;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.link.model.Link;
import link.srrrg.link.repository.LinkRepository;
import link.srrrg.link.repository.LinkUtmValueRepository;
import link.srrrg.link.repository.LinkUtmValueRepository.EffectiveUtmValueByLink;

/**
 * 캠페인 링크 목록 조회의 application 경계다. 캠페인 접근 권한과 소유 프로젝트를 확인한 뒤
 * 커서 페이지를 만들고, 웹 화면에 필요한 유효 UTM을 일괄 조회한다.
 *
 * <p>링크 생성·수정 정책과 독립적으로 변경되는 읽기 모델이라 {@link CampaignService}에 넣지 않았다.
 * 웹과 공개 API는 인증 방식과 응답 필드가 다르지만 링크 정렬과 커서 규칙은 이 클래스에서 공유한다.</p>
 */
@Service
public class CampaignLinkQueryService {
	private final CampaignService campaignService;
	private final LinkRepository linkRepository;
	private final LinkUtmValueRepository linkUtmValueRepository;

	public CampaignLinkQueryService(CampaignService campaignService, LinkRepository linkRepository,
			LinkUtmValueRepository linkUtmValueRepository) {
		this.campaignService = campaignService;
		this.linkRepository = linkRepository;
		this.linkUtmValueRepository = linkUtmValueRepository;
	}

	/**
	 * 웹 사용자의 VIEWER 권한을 확인하고 현재 페이지의 유효 UTM까지 조회한다.
	 * 다음 페이지 판정용 여분 링크는 화면에 보이지 않으므로 UTM 조회 대상에서 제외한다.
	 */
	@Transactional(readOnly = true)
	public CampaignLinkQueryResult listForUser(Long userId, Long campaignId, Long cursor, int limit) {
		campaignService.get(userId, campaignId);
		CampaignLinkQueryResult page = page(campaignId, cursor, limit);
		if (page.items().isEmpty()) return page;
		List<CampaignEffectiveUtmValue> effectiveUtmValues = linkUtmValueRepository
				.findEffectiveByLinkIds(page.items().stream().map(Link::getId).toList())
				.stream().map(CampaignLinkQueryService::toEffectiveUtmValue).toList();
		return new CampaignLinkQueryResult(page.items(), page.nextCursor(), effectiveUtmValues);
	}

	/**
	 * API key의 프로젝트에 속한 캠페인인지 확인한 뒤 공개 API 링크 페이지를 조회한다.
	 * scope와 API key 프로젝트 일치는 Controller의 공통 authorizer가 선행해서 확인한다.
	 */
	@Transactional(readOnly = true)
	public CampaignLinkQueryResult listForApiKey(Long projectId, Long campaignId, Long cursor, int limit) {
		campaignService.findForApiKey(projectId, campaignId);
		return page(campaignId, cursor, limit);
	}

	/**
	 * id 내림차순 커서 페이지를 만든다. {@code limit + 1}개를 조회해 별도 count 쿼리 없이 다음 페이지를
	 * 판정하고, 응답에는 여분 행을 제거한 뒤 마지막으로 노출한 링크 id를 cursor로 사용한다.
	 */
	private CampaignLinkQueryResult page(Long campaignId, Long cursor, int limit) {
		PageRequest request = PageRequest.of(0, limit + 1);
		List<Link> found = cursor == null
				? linkRepository.findByCampaignIdOrderByIdDesc(campaignId, request)
				: linkRepository.findByCampaignIdAndIdLessThanOrderByIdDesc(campaignId, cursor, request);
		List<Link> items = found.size() > limit ? found.subList(0, limit) : found;
		Long nextCursor = found.size() > limit ? items.get(items.size() - 1).getId() : null;
		return new CampaignLinkQueryResult(List.copyOf(items), nextCursor, List.of());
	}

	private static CampaignEffectiveUtmValue toEffectiveUtmValue(EffectiveUtmValueByLink value) {
		return new CampaignEffectiveUtmValue(
				value.getLinkId(), value.getFieldName(), value.getValue(), value.getSource());
	}
}
