package link.srrrg.link.risk.model;

/** 위험 검사를 완료하지 못해 안전 판정이 없으며, fail-closed 정책에 따라 503으로 변환되는 상태다. */
public class UrlRiskCheckFailedException extends RuntimeException {
	public UrlRiskCheckFailedException() {
		super("URL 안전 검사를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
	}
}
