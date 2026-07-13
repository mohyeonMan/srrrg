package link.srrrg.link;

public class UrlRiskCheckFailedException extends RuntimeException {
	public UrlRiskCheckFailedException() {
		super("URL 안전 검사를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
	}
}
