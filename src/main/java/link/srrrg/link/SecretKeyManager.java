package link.srrrg.link;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import link.srrrg.common.util.SecureRandomStringGenerator;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SecretKeyManager {

	private static final String SECRET_KEY_PREFIX = "srrrg_sk_";
	private static final String URL_SAFE_CHARACTERS =
			"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
	private static final int SECRET_LENGTH = 43;

	private final SecureRandomStringGenerator randomStringGenerator;

	public GeneratedSecretKey generate() {
		String value = SECRET_KEY_PREFIX
				+ randomStringGenerator.generate(URL_SAFE_CHARACTERS, SECRET_LENGTH);
		return new GeneratedSecretKey(value, HexFormat.of().formatHex(digest(value)));
	}

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
