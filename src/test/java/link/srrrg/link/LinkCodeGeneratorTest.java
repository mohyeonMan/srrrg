package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import link.srrrg.common.util.SecureRandomStringGenerator;

class LinkCodeGeneratorTest {

	private final LinkCodeGenerator generator =
			new LinkCodeGenerator(new SecureRandomStringGenerator());

	@Test
	void generatesSixBase62Characters() {
		String code = generator.generate();

		assertThat(code).hasSize(6).matches("[0-9A-Za-z]{6}");
	}

	@Test
	void doesNotAlwaysGenerateSameCode() {
		Set<String> codes = new HashSet<>();
		for (int count = 0; count < 20; count++) {
			codes.add(generator.generate());
		}

		assertThat(codes).hasSizeGreaterThan(1);
	}
}
