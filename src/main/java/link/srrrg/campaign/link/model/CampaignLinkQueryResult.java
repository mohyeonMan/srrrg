package link.srrrg.campaign.link.model;


import java.util.List;

import link.srrrg.link.model.Link;

/**
 * 캠페인 링크 목록 유스케이스의 내부 결과다. HTTP 응답 DTO와 분리해 Service가 링크 엔티티를
 * API 계약으로 직접 노출하지 않으면서도, 페이지 정보와 화면용 유효 UTM을 한 번에 전달한다.
 *
 * <p>공개 API는 유효 UTM을 응답하지 않으므로 {@code effectiveUtmValues}가 빈 목록이다.
 * 웹 목록은 현재 페이지에 실제로 노출되는 링크의 값만 포함한다.</p>
 */
public record CampaignLinkQueryResult(
		List<Link> items,
		Long nextCursor,
		List<CampaignEffectiveUtmValue> effectiveUtmValues) {
}
