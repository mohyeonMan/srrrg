package link.srrrg.auth;

/**
 * 이미 소비된 refresh token이 다시 들어왔다는 신호. 단순한 인증 실패가 아니라 토큰 원문이
 * 둘 이상 존재한다는 뜻이므로, 이 예외가 던져질 때는 같은 계열의 세션이 모두 폐기된 뒤다.
 */
public class RefreshTokenReuseException extends RuntimeException {
	public RefreshTokenReuseException() {
		super("이미 사용된 refresh token입니다.");
	}
}
