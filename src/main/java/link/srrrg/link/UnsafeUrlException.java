package link.srrrg.link;

/**
 * 위험 검사에서 위협으로 판정된 URL. 재시도해도 결과가 같은 영구 거부라 4xx로 변환된다.
 * 검사를 수행하지 못한 경우와 구분해야 하므로 {@link UrlRiskCheckFailedException}과 나눠 둔다.
 */
public class UnsafeUrlException extends RuntimeException {
	public UnsafeUrlException() {
		super("Google Safe Browsing에서 잠재적인 피싱 또는 악성 사이트로 분류한 URL입니다.");
	}
}
