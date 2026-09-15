package link.srrrg.campaign.importing;

/**
 * 요청한 임포트가 지정한 캠페인에 속하지 않거나 존재하지 않을 때 발생한다.
 * 두 경우를 구분하지 않아 다른 캠페인의 임포트 식별자를 탐색하는 데 응답이 이용되지 않게 한다.
 */
public class CampaignImportNotFoundException extends RuntimeException {
	public CampaignImportNotFoundException() {
		super("import를 찾을 수 없습니다.");
	}
}
