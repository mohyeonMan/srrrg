package link.srrrg.link;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import link.srrrg.common.util.SecureRandomStringGenerator;
import lombok.RequiredArgsConstructor;

/**
 * 익명 링크의 관리 자격증명을 만들고 대조한다. 로그인 없이 만든 링크를 나중에 수정·삭제할 수 있는
 * 유일한 근거가 이 값이라, 사실상 그 링크의 비밀번호다.
 *
 * <p>원문은 생성 응답에 한 번만 나가고 DB에는 SHA-256 해시만 남는다. 재발급 수단이 없으므로
 * 사용자가 잃어버리면 그 링크는 더 이상 관리할 수 없다.</p>
 *
 * <p>대조에 {@link MessageDigest#isEqual}을 쓰는 것은 앞자리부터 비교하며 일찍 빠져나가는
 * 문자열 비교가 응답 시간 차이로 정답 일부를 알려줄 수 있기 때문이다.</p>
 */
@Component
@RequiredArgsConstructor
public class SecretKeyManager {

	private static final String SECRET_KEY_PREFIX = "srrrg_sk_";
	private static final String URL_SAFE_CHARACTERS =
			"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
	private static final int SECRET_LENGTH = 43;

	private final SecureRandomStringGenerator randomStringGenerator;

	/**
	 * 새 secret key와 저장용 해시를 함께 만든다. 접두사는 유출된 문자열의 용도를 알아보게 하려는 표시이고,
	 * 뒤의 43자는 64자 집합에서 뽑으므로 약 256비트다.
	 *
	 * @return 사용자에게 한 번만 보여줄 원문과 DB에 저장할 해시
	 */
	public GeneratedSecretKey generate() {
		String value = SECRET_KEY_PREFIX
				+ randomStringGenerator.generate(URL_SAFE_CHARACTERS, SECRET_LENGTH);
		return new GeneratedSecretKey(value, HexFormat.of().formatHex(digest(value)));
	}

	/**
	 * 제시된 secret key가 저장된 해시와 맞는지 확인한다. 형식이 틀리거나 저장된 해시가 깨져 있으면
	 * 예외 대신 거짓을 돌려준다. 호출자는 이 결과를 링크 없음과 같은 404로 합쳐, 코드가 존재하는지 여부까지 감춘다.
	 */
	public boolean matches(String value, String hash) {
		if (!hasValidFormat(value) || hash == null) {
			return false;
		}
		try {
			return MessageDigest.isEqual(digest(value), HexFormat.of().parseHex(hash));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private byte[] digest(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	/**
	 * 해시 계산 전에 형태부터 거른다. 헤더 값은 누구나 채울 수 있으므로 길이와 문자 집합이 다른 값은
	 * 대조 대상으로 삼지 않는다.
	 */
	private boolean hasValidFormat(String value) {
		if (value == null || !value.startsWith(SECRET_KEY_PREFIX)
				|| value.length() != SECRET_KEY_PREFIX.length() + SECRET_LENGTH) {
			return false;
		}

		for (int index = SECRET_KEY_PREFIX.length(); index < value.length(); index++) {
			if (URL_SAFE_CHARACTERS.indexOf(value.charAt(index)) < 0) {
				return false;
			}
		}
		return true;
	}

	public record GeneratedSecretKey(String value, String hash) {
	}
}
