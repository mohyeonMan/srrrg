package link.srrrg.auth;

/**
 * 형식 오류, 존재하지 않는 토큰, 이미 폐기되었거나 만료된 토큰을 한데 묶는 예외.
 * 어느 쪽인지 구분해 알리지 않는 것은 유효한 토큰 문자열을 탐색하는 데 단서를 주지 않기 위해서다.
 * {@code AuthController}가 이 예외를 401로 바꾸면서 쿠키까지 지운다.
 */
public class InvalidRefreshTokenException extends RuntimeException {
	public InvalidRefreshTokenException() {
		super("refresh token이 유효하지 않습니다.");
	}
}
