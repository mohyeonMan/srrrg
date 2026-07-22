package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

	@Test
	void skipsCodesReservedByApplicationRoutes() {
		SecureRandomStringGenerator random = mock(SecureRandomStringGenerator.class);
		when(random.generate(LinkCodeGenerator.BASE62_CHARACTERS, LinkCodeGenerator.CODE_LENGTH))
				.thenReturn("manage", "aB3x9Q");

		assertThat(new LinkCodeGenerator(random).generate()).isEqualTo("aB3x9Q");
	}
}
