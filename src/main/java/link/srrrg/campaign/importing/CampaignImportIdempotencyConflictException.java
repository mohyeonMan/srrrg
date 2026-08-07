package link.srrrg.campaign.importing;

public class CampaignImportIdempotencyConflictException extends RuntimeException {
	public CampaignImportIdempotencyConflictException() {
		super("같은 Idempotency-Key를 다른 CSV 업로드에 사용할 수 없습니다.");
	}
}
