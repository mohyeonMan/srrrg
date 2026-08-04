package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private static final String OLD_KEY = encoded("0123456789abcdef0123456789abcdef");
	private static final String NEW_KEY = encoded("abcdef0123456789abcdef0123456789");

	@Test
	void issuesAndVerifiesMinimumClaims() {
		JwtService service = service(new JwtProperties("current", NEW_KEY, "", ""));

		String token = service.issue(42L);

		assertThat(service.verify(token)).isEqualTo(42L);
		assertThatThrownBy(() -> service.verify(token + "tampered"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void verifiesPreviousKeyByKidDuringRotation() {
		String oldToken = service(new JwtProperties("old", OLD_KEY, "", "")).issue(7L);
		JwtService rotated = service(new JwtProperties("new", NEW_KEY, "old", OLD_KEY));

		assertThat(rotated.verify(oldToken)).isEqualTo(7L);
	}

	@Test
	void rejectsShortSigningKey() {
		JwtService service = service(new JwtProperties("short", encoded("too-short"), "", ""));

		assertThatThrownBy(service::requireConfigured)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("256bit");
	}

	private JwtService service(JwtProperties properties) {
		return new JwtService(properties, "https://srrrg.link");
	}

	private static String encoded(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}
}
