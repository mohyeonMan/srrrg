package link.srrrg.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * refresh token, OAuth state, 계정 연결 토큰처럼 DB에 원문을 남기면 안 되는 값을 조회용 해시로 바꾼다.
 *
 * <p>비밀번호에 쓰는 bcrypt 계열 대신 단순 SHA-256을 쓰는 이유는 두 가지다. 여기서 다루는 값은 사람이 만든
 * 비밀번호가 아니라 {@code SecureRandom}으로 만든 고엔트로피 난수라 사전 대입이 성립하지 않고,
 * 매 요청마다 해시로 행을 찾아야 해서 같은 입력이 항상 같은 결과를 내야 한다.</p>
 *
 * <p>같은 이유로 솔트도 쓰지 않는다. 솔트를 넣으면 토큰 원문 없이는 행을 찾을 수 없다.</p>
 */
final class TokenHash {

	private TokenHash() {
	}

	static String sha256(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}
}
