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
		return passwordEncoder.matches(value, hash);
	}

	public record GeneratedSecretKey(String value, String hash) {
	}
}
