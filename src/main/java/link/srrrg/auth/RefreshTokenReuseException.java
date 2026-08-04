package link.srrrg.auth;

public class RefreshTokenReuseException extends RuntimeException {
	public RefreshTokenReuseException() {
		super("이미 사용된 refresh token입니다.");
	}
}
