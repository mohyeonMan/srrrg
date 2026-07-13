package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RedirectPageAssetTest {

	@Test
	void templateEscapesTheDisplayedDestinationAndUsesAnExternalScript() throws IOException {
		String html = Files.readString(Path.of("src/main/resources/templates/redirect-confirm.html"));
		assertThat(html).contains("th:text=\"${originalUrl}\"");
		assertThat(html).contains("th:src=\"@{/js/redirect-confirm.js}\"");
		assertThat(html).doesNotContain("<script>");
	}

	@Test
	void clientRunsOneCheckWithTimeoutAndUsesReplaceOnlyAfterAResult() throws IOException {
		String script = Files.readString(Path.of("src/main/resources/static/js/redirect-confirm.js"));
		assertThat(count(script, "fetch(")).isEqualTo(1);
		assertThat(script).contains("new AbortController()", "5000", "600", "250");
		assertThat(script.indexOf("const cachedStatus")).isLessThan(script.indexOf("new AbortController()"));
		assertThat(script.substring(script.indexOf("const cachedStatus"), script.indexOf("new AbortController()")))
				.contains("return;");
		assertThat(script).contains("window.location.replace(url)");
		assertThat(script).contains("window.history.length > 1", "window.history.back()");
		assertThat(script).doesNotContain("document.referrer");
		assertThat(script).doesNotContain("innerHTML", "window.location.href");
	}

	private int count(String value, String needle) {
		return (value.length() - value.replace(needle, "").length()) / needle.length();
	}
}
