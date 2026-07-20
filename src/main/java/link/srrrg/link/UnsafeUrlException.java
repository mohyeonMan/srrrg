package link.srrrg.link;

public class UnsafeUrlException extends RuntimeException {
	public UnsafeUrlException() {
		super("Google Safe Browsing에서 잠재적인 피싱 또는 악성 사이트로 분류한 URL입니다.");
	}
}
