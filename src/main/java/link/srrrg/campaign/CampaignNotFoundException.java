package link.srrrg.campaign;

public class CampaignNotFoundException extends RuntimeException {
	public CampaignNotFoundException() {
		super("캠페인을 찾을 수 없습니다.");
	}
}
