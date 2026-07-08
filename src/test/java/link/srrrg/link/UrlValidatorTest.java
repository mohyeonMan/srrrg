package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

	private final UrlValidator validator = new UrlValidator();

	@ParameterizedTest
	@ValueSource(strings = {
			"http://example.com",
			"https://example.com/path?query=value"
	})
	void acceptsPublicHttpUrls(String value) {
		assertThatCode(() -> validator.validate(value)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/relative/path",
			"mailto:user@example.com",
			"javascript:alert(1)",
			"https:///missing-host"
	})
	void rejectsInvalidOrUnsupportedUrls(String value) {
		assertThatThrownBy(() -> validator.validate(value))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://localhost",
			"http://127.0.0.1",
			"http://10.0.0.1",
			"http://172.16.0.1",
			"http://192.168.0.1",
			"http://[::1]"
	})
	void rejectsLocalAndPrivateAddresses(String value) {
		assertThatThrownBy(() -> validator.validate(value))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsUrlLongerThanMaximum() {
		String value = "https://example.com/" + "a".repeat(2048);

		assertThatThrownBy(() -> validator.validate(value))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
