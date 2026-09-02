package link.srrrg.campaign.importing;

/**
 * 같은 Idempotency-Key로 내용이 다른 CSV를 올린 경우. 이전 결과를 돌려주면
 * 클라이언트가 자기가 올린 것과 다른 임포트를 받게 되므로 충돌로 거부한다.
 */
public class CampaignImportIdempotencyConflictException extends RuntimeException {
	public CampaignImportIdempotencyConflictException() {
		super("같은 Idempotency-Key를 다른 CSV 업로드에 사용할 수 없습니다.");
	}
}
