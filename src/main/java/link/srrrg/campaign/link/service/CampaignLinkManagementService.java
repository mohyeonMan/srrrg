package link.srrrg.campaign.link.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import link.srrrg.campaign.service.CampaignService;
import link.srrrg.link.repository.LinkRepository;
import lombok.RequiredArgsConstructor;

/** 캠페인에 이미 속한 링크의 변경 흐름을 담당한다. */
@Service
@RequiredArgsConstructor
public class CampaignLinkManagementService {
	private final CampaignService campaigns;
	private final LinkRepository links;

	/**
	 * 선택 코드를 한 트랜잭션에서 삭제하며, 하나라도 해당 캠페인 소유가 아니면 전체를 실패시킨다.
	 * 삭제 개수 비교가 다른 캠페인의 코드 존재 여부를 드러내지 않는 권한 경계도 겸한다.
	 */
	@Transactional
	public int delete(Long userId, Long campaignId, List<String> codes) {
		campaigns.requireEditableCampaign(userId, campaignId);
		List<String> uniqueCodes = new ArrayList<>(new LinkedHashSet<>(codes));
		int deletedCount = links.softDeleteByCampaignIdAndCodeIn(campaignId, uniqueCodes);
		if (deletedCount != uniqueCodes.size()) {
			throw new IllegalArgumentException("선택한 링크 중 삭제할 수 없는 링크가 있습니다.");
		}
		return deletedCount;
	}
}
