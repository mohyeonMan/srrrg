package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.link.SecretKeyManager.GeneratedSecretKey;

class SecretKeyManagerTest {

	private final SecretKeyManager manager =
			new SecretKeyManager(new SecureRandomStringGenerator());

	@Test
	void generatesPrefixedSecretAndBcryptHash() {
		GeneratedSecretKey generated = manager.generate();

		assertThat(generated.value()).startsWith("srrrg_sk_");
		assertThat(generated.hash()).doesNotContain(generated.value());
		assertThat(manager.matches(generated.value(), generated.hash())).isTrue();
	}

	@Test
	void rejectsMalformedSecretBeforeMatching() {
		GeneratedSecretKey generated = manager.generate();

		assertThat(manager.matches(null, generated.hash())).isFalse();
		assertThat(manager.matches("srrrg_sk_short", generated.hash())).isFalse();
		assertThat(manager.matches(generated.value() + "!", generated.hash())).isFalse();
	}
}
