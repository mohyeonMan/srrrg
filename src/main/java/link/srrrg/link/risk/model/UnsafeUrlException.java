package link.srrrg.link.risk.model;

/**
 * 위험 검사에서 위협으로 판정된 URL이다. 재시도해도 같은 영구 거부이므로 4xx로 변환하며,
 * 판정을 얻지 못한 {@link UrlRiskCheckFailedException}과 구분한다.
 */
public class UnsafeUrlException extends RuntimeException {
	public UnsafeUrlException() {
		super("Google Safe Browsing에서 잠재적인 피싱 또는 악성 사이트로 분류한 URL입니다.");
	}
}
