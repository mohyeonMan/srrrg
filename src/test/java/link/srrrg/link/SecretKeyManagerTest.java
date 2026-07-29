package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.link.SecretKeyManager.GeneratedSecretKey;

class SecretKeyManagerTest {

	private final SecretKeyManager manager =
			new SecretKeyManager(new SecureRandomStringGenerator());

	@Test
	void generatesPrefixedSecretAndSha256Hash() {
		GeneratedSecretKey generated = manager.generate();

		assertThat(generated.value()).startsWith("srrrg_sk_");
		assertThat(generated.hash()).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(manager.matches(generated.value(), generated.hash())).isTrue();
		assertThat(manager.matches(manager.generate().value(), generated.hash())).isFalse();
	}

	@Test
	void rejectsMalformedSecretBeforeMatching() {
		GeneratedSecretKey generated = manager.generate();

		assertThat(manager.matches(null, generated.hash())).isFalse();
		assertThat(manager.matches("srrrg_sk_short", generated.hash())).isFalse();
		assertThat(manager.matches(generated.value() + "!", generated.hash())).isFalse();
		assertThat(manager.matches(generated.value(), "invalid")).isFalse();
	}
}
