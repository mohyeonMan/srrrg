package link.srrrg.project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 초대 토큰과 API 멱등 키의 해시를 만든다. 초대 토큰은 원문을 저장하지 않기 위해,
 * 멱등 키의 요청 지문은 긴 요청 내용을 고정 길이로 줄이기 위해 쓴다.
 *
 * <p>고엔트로피 난수와 내부 지문에만 쓰므로 솔트 없는 SHA-256으로 충분하다.
 * 사람이 만든 비밀번호에는 쓰지 않는다.</p>
 */
final class InvitationTokenHash {
	private InvitationTokenHash() {
	}

	static String sha256(String token) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
