package link.srrrg.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecureRandomStringGeneratorTest {

	private final SecureRandomStringGenerator generator = new SecureRandomStringGenerator();

	@Test
	void generatesRequestedLengthFromGivenCharacters() {
		String result = generator.generate("ABC123", 32);

		assertThat(result).hasSize(32).matches("[ABC123]+");
	}

	@Test
	void rejectsEmptyCharacters() {
		assertThatThrownBy(() -> generator.generate("", 6))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNonPositiveLength() {
		assertThatThrownBy(() -> generator.generate("ABC", 0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
