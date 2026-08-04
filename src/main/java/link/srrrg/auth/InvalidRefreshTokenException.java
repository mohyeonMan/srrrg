package link.srrrg.auth;

public class InvalidRefreshTokenException extends RuntimeException {
	public InvalidRefreshTokenException() {
		super("refresh token이 유효하지 않습니다.");
	}
}
