package link.srrrg.link;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public GeneratedSecretKey generate() {
		String value = SECRET_KEY_PREFIX
				+ randomStringGenerator.generate(URL_SAFE_CHARACTERS, SECRET_LENGTH);
		return new GeneratedSecretKey(value, passwordEncoder.encode(value));
	}

	public boolean matches(String value, String hash) {
		if (!hasValidFormat(value)) {
			return false;
		}
		return passwordEncoder.matches(value, hash);
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
