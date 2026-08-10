package link.srrrg;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UtmTestPageAssetTest {

	@Test
	void displaysEveryQueryParameterWithoutAssumingFieldNames() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/utm-test.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-utm-test.js"));

		assertThat(template).contains(
				"id=\"request-url\"",
				"id=\"query-parameter-rows\"",
				"id=\"query-parameter-count\"");
		assertThat(script).contains(
				"new URLSearchParams(window.location.search).entries()",
				"nameCell.textContent = name",
				"valueCell.textContent = value");
		assertThat(script).doesNotContain("utm_source", "utm_medium", "innerHTML");
	}
}
