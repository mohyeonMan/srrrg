package link.srrrg.campaign;

/**
 * 캠페인이 없거나 요청한 프로젝트의 것이 아닌 경우. 두 상황을 합치는 것은
 * 다른 프로젝트에 어떤 캠페인이 있는지 알려주지 않기 위해서다.
 */
public class CampaignNotFoundException extends RuntimeException {
	public CampaignNotFoundException() {
		super("캠페인을 찾을 수 없습니다.");
	}
}
